package models.applications;


import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import io.jenetics.engine.EvolutionResult;
import models.auxiliary.Constraint;
import models.auxiliary.Latency;
import models.auxiliary.MinMax;
import models.auxiliary.Normalization;
import models.enums.NormalizedMethod;
import models.enums.ObjectiveFunction;
import models.enums.QoS;
import org.javatuples.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class UtilityApplication extends Application {
    /* Store the information of table 3 -> QMax-
        for each service and QoS attribute, it stores the máx value.

        Ex. {s1 {Cost = 25.6, Time = 8.35}, s2 {Cost... }}

        Analogously, the same is done for the minimum
     */

    // Stores the min and max values of each service by looking at all providers and is stored for each quality of
    // service attribute.
    private final Map<Integer, Map<QoS, MinMax>> _qMinMax = new LinkedHashMap<>();

    private final Map<Integer, Map<QoS, List<Double>>>
        _qDegreeMatrix = new LinkedHashMap<>(),
        _qDegreeMatrixNorm = new LinkedHashMap<>(),
        _probabilityMatrix = new LinkedHashMap<>(),
        _utilityMatrix = new LinkedHashMap<>(),
        _utilityMatrixNorm = new LinkedHashMap<>();

    /**
     * - The value that takes the application with the max-min values of each QoS Attribute
     * - Constraints converted to fitness constraints. This is the fitness that user will expect of the deployment
     */
    private final Map<QoS, MinMax> _qMinMaxAggregated = new HashMap<>();

    // Constraints that user gives us (normalized)
    private final Map<QoS, Constraint> _normalizedConstraints = new HashMap<>();
    // This is the fitness that each provider has
    private final Map<Integer, Map<Integer, Double>> _utilityProvider = new HashMap<>();
    private final Map<Integer, Double> _componentRequiredUtility = new HashMap<>();
    private final Map<Integer, Map<Integer, Double>> _latency = new ConcurrentHashMap<>();
    // Keeps the information of the ranges of the matrix
    private final Integer _nOfDegrees;

    public UtilityApplication(Application o) {
        this(o, new HashMap<>());
    }

    public UtilityApplication(Application o, Integer degrees) {
        this(o, new HashMap<>(), degrees);
    }

    public UtilityApplication(Application o, Map<QoS, Constraint> constraint) {
        this(o, constraint, o._nOfQoS);
    }

    public UtilityApplication(Application o, Map<QoS, Constraint> constraint, Integer degrees) {
        super(o);

        if (o._channelQoS.contains(QoS.LATENCY)) {
            Latency.fillLatencyPerProviders(o, _latency);
        }

        // Lk is size of the biggest service group.
        Integer _lk = extractLk();

        if (degrees < 1 || degrees > _lk) {
            throw new RuntimeException(String.format("Degrees must be in range (1, %d) (1 <= degrees <= Lk)", _lk));
        }

        // Define the desired quality-degree
        _nOfDegrees = degrees;

        /*
         * Next steps we are building all tables necessary to use fitness approach:
         *
         * 1. MinMax matrix, which shall be the minimum and maximum values that can be reached by the different
         * components per attribute.
         *
         * 2. QualityDegree matrix, which shall be the matrix containing each of the degrees of quality.
         *
         * 3. Probability matrix, which will indicate the probability that each supplier will be able to meet the
         * different quality grades.
         *
         * 4. Utility matrix (and normalized), which will be the tables with the final fitness for each supplier
         * (and the normalized fitness).
         *
         * 5. At last, qMinMaxAggregated and qMaxAggregated, where we store the min and max value for each of the QoS.
         */

        // Extract min-max per component
        minMaxPerComponent();
        // Calculate the normalized and non-normalized quality degree matrix
        qDegreeMatrix();
        // Calculate the probability matrix
        probabilityMatrix();
        // Prepare the normalized and non-normalized fitness matrix
        utilityNormalized();
        // Extract Min & Max aggregated for the application
        qMinMaxAggregated();
        // Normalize constraints
        normalizedConstraints();
        // Extract "fitness" from providers
        providersUtility();
    }

    public Map<Integer, Map<QoS, List<Double>>> getQDegreeMatrixNorm() {
        return _qDegreeMatrixNorm;
    }

    private Integer extractLk() {
        return getServices().parallelStream().mapToInt(s -> s.getCandidates().size()).max().orElse(0);
    }

    /**
     * For each service and quality of service attribute,
     * it returns the highest and lowest values of all its providers.
     */
    public void minMaxPerComponent() {
        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            Integer iService = e.getKey(), iGenotype = e.getValue();
            Service s = getService(iService);
            _minMaxPerComponent(iService, iGenotype, s.getCandidates(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            Integer iGate = e.getKey(), iGenotype = e.getValue();
            Gate g = getGate(iGate);
            _minMaxPerComponent(iGate, iGenotype, g.getCandidates(), _channelQoS);
        }
    }

    private void
    _minMaxPerComponent(Integer iComponent, Integer iGenotype, List<Integer> candidates, List<QoS> qosList) {
        // Get solution for this service and append this min-value.
        Map<QoS, MinMax> v = _qMinMax.getOrDefault(iGenotype, new ConcurrentHashMap<>());

        for (QoS k : qosList) {
            // Prepare a new min-max instance
            MinMax minMax = new MinMax();

            switch (k) {
                case LATENCY -> _latency.get(iComponent).entrySet().parallelStream()
                    .mapToDouble(Map.Entry::getValue)
                    .forEach(minMax::setMinMax);
                case THROUGHPUT -> candidates.parallelStream()
                    .mapToDouble(candidate -> getProvider(candidate).getConnRange().getCapacity())
                    .forEach(minMax::setMinMax);
                default -> candidates.parallelStream()
                    .mapToDouble(candidate -> getProvider(candidate).getAttributeValue(k))
                    .forEach(minMax::setMinMax);
            }

            // Add qos solution
            v.put(k, minMax);
        }

        // Add to global solution
        _qMinMax.put(iGenotype, v);
    }

    /**
     * Contains the range that providers can have.
     * <p>
     * If minimum for Cost is 35 and maximum 55, having 4 QoS would be:
     * [35, 40, 45, 50, 55]
     */
    public void qDegreeMatrix() {
        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            _qualityDegreeMatrix(e.getValue(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            _qualityDegreeMatrix(e.getValue(), _channelQoS);
        }
    }

    private void _qualityDegreeMatrix(Integer iGenotype, List<QoS> kList) {
        // Define numeric values
        double delta;
        // Get solution for this service
        Map<QoS, List<Double>>
            vNorm = _qDegreeMatrixNorm.getOrDefault(iGenotype, new ConcurrentHashMap<>()),
            v = _qDegreeMatrix.getOrDefault(iGenotype, new ConcurrentHashMap<>());

        for (QoS k : kList) {
            // Prepare aux for values
            List<Double> auxNorm = new ArrayList<>(), aux = new ArrayList<>();
            // For each table, qMin and qMax, we take the value of service i and qoS j.
            MinMax minMax = _qMinMax.get(iGenotype).get(k);
            // Define qD, qMin and qMax
            double qD = minMax.getMin(), qMin = minMax.getMin(), qMax = minMax.getMax(), qNorm;
            // Delta is the offset applied for avoiding a 0.
            delta = (qMax - qMin) / _nOfDegrees;
            // Get qMin and qMax to normalize
            double qMinNorm = qMin - delta, qMaxNorm = qMax + delta;

            for (int d = 0; d < _nOfDegrees; d++) {
                // Add delta and save into quality degree matrix
                qD += delta;
                aux.add(qD);

                // Normalize qD and save into norm-quality degree matrix
                if (k.getObjective().equals(ObjectiveFunction.MAXIMIZE)) {
                    qNorm = (qD - qMinNorm) / (qMax - qMinNorm);
                } else {
                    qNorm = (qMaxNorm - qD) / (qMaxNorm - qMin);
                }

                auxNorm.add(qNorm);
            }

//            // Add each quality-degree
//            for (int d = 1; d <= _nOfDegrees; d++) {
//                // Q_{d} = Q_{d - 1} + d * delta
//                qD = qD + d * delta;
//
//                // Add non-normalized value
//                aux.add(qD);
//
//                // Normalize value
//                if (k.getObjective().equals(ObjectiveFunction.MAXIMIZE)) {
//                    qMinNorm = qMin - delta;
//                    qD = (qD - qMinNorm) / (qMax - qMinNorm);
//                } else {
//                    qMaxNorm = qMax + delta;
//                    qD = (qMaxNorm - qD) / (qMaxNorm - qMin);
//                }
//
//                // Add normalized value
//                auxNorm.add(qD);
//            }

            // Append this quality degree.
            vNorm.put(k, auxNorm);
            v.put(k, aux);
        }

        // Update dictionary
        _qDegreeMatrixNorm.put(iGenotype, vNorm);
        _qDegreeMatrix.put(iGenotype, v);
    }

    /**
     * Contains the amount of providers available for that service that can satisfied
     * that:  count(valuesOfProviderWithSameQoSi) <= degree / numberOfServicesForQoSi
     */
    public void probabilityMatrix() {
        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            Integer iService = e.getKey(), iGenotype = e.getValue();
            Service s = getService(iService);
            _probabilityMatrix(iGenotype, s.getCandidates(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            Integer iGate = e.getKey(), iGenotype = e.getValue();
            Gate g = getGate(iGate);
            _probabilityMatrix(iGenotype, g.getCandidates(), _channelQoS);
        }
    }

    private void _probabilityMatrix(Integer iGenotype, List<Integer> candidates, List<QoS> kList) {
        // Get probabilities
        Map<QoS, List<Double>> map = _probabilityMatrix.getOrDefault(iGenotype, new HashMap<>());
        // Get candidates size
        double cSize = candidates.size();

        for (QoS k : kList) {
            // Get all values
            List<Double> values = _qDegreeMatrix.get(iGenotype).get(k);

            // Prepare probabilities list
            List<Double> probabilities = values.stream().mapToDouble(v -> {
                // Get number of providers that satisfied the value indicated (v)
                double counter = candidates.parallelStream().filter(p -> switch (k) {
                    case COST, RESPONSE_TIME -> v >= getProvider(p).getAttributeValue(k);
                    case LATENCY -> v >= _latency.get(iGenotype).get(p);
                    case THROUGHPUT -> v <= getProvider(p).getConnRange().getCapacity();
                    default -> v <= getProvider(p).getAttributeValue(k);
                }).count();

                // Return the probability of satisfied that value
                return counter / cSize;
            }).boxed().collect(Collectors.toList());

            // Save probabilities
            map.put(k, probabilities);
        }

        // Update probability
        _probabilityMatrix.put(iGenotype, map);
    }

    /**
     * Defines the degrees available between 0+getDelta and 1. For example:
     * <p>
     * [0.25, 0.5, 0.75, 1]
     */
    public void utilityMatrix() {
        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            _utilityMatrix(e.getValue(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            _utilityMatrix(e.getValue(), _channelQoS);
        }
    }

    private void _utilityMatrix(Integer iGenotype, List<QoS> kList) {
        // Get utilities
        Map<QoS, List<Double>> v = _utilityMatrix.getOrDefault(iGenotype, new HashMap<>());

        // Define variables
        List<Double> values, utilities;

        for (QoS k : kList) {
            MinMax minMax = _qMinMax.get(iGenotype).get(k);
            double kiValueMax = minMax.getMax(), kiValueMin = minMax.getMin();
            double delta = (kiValueMax - kiValueMin) / _nOfDegrees, qikDelta;
            values = _qDegreeMatrix.get(iGenotype).get(k);

            if (k.getObjective().equals(ObjectiveFunction.MAXIMIZE)) {
                qikDelta = kiValueMin - delta;
                utilities = values.stream()
                    .mapToDouble(c -> (c - qikDelta) / (kiValueMax - qikDelta))
                    .boxed().collect(Collectors.toList());
            } else {
                qikDelta = kiValueMax + delta;
                utilities = values.stream()
                    .mapToDouble(c -> (qikDelta - c) / (qikDelta - kiValueMin))
                    .boxed().collect(Collectors.toList());
            }

            // Save utilities
            v.put(k, utilities);
        }

        // Save utilities
        _utilityMatrix.put(iGenotype, v);
    }

    /**
     * This matrix is the one that will be used when we encode the genome.
     * It's calculated multiplying probability Matrix and fitness Matrix
     **/
    public void utilityNormalized() {
        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            _utilityNormalized(e.getValue(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            _utilityNormalized(e.getValue(), _channelQoS);
        }
    }

    private void _utilityNormalized(Integer iGenotype, List<QoS> kList) {
        // Extract map
        Map<QoS, List<Double>> v = _utilityMatrixNorm.getOrDefault(iGenotype, new HashMap<>());

        for (QoS k : kList) {
            List<Double>
                uNormalized = new ArrayList<>(),
                probabilities = _probabilityMatrix.get(iGenotype).get(k),
                qDegrees = _qDegreeMatrixNorm.get(iGenotype).get(k);

            for (int i = 0; i < probabilities.size(); i++) {
                uNormalized.add(qDegrees.get(i) * probabilities.get(i));
            }

            // Save fitness
            v.put(k, uNormalized);
        }

        // Save utilities
        _utilityMatrixNorm.put(iGenotype, v);
    }

    public void qMinMaxAggregated() {
        for (QoS k : _qosList) {
            if (k.equals(QoS.LATENCY)) {
                // Extract min-max for each provider
                Map<Integer, MinMax> latencyMap = _latency.entrySet().parallelStream().map(e -> {
                    // Get all candidates
                    Map<Integer, Double> candidates = e.getValue();
                    // Instance a min-max to store results
                    MinMax minMaxLocal = new MinMax();

                    candidates.entrySet().parallelStream()
                        .mapToDouble(Map.Entry::getValue)
                        .forEach(minMaxLocal::setMinMax);

                    return new AbstractMap.SimpleEntry<>(e.getKey(), minMaxLocal);
                }).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

                // Sum all application values
                double
                    min = latencyMap.values().parallelStream().mapToDouble(MinMax::getMin).reduce(0, Double::sum),
                    max = latencyMap.values().parallelStream().mapToDouble(MinMax::getMax).reduce(0, Double::sum);

                // Save that values
                _qMinMaxAggregated.put(k, new MinMax(min, max));
            } else {
                Normalization norm = getAppNorm().get(k);
                _qMinMaxAggregated.put(k, new MinMax(norm.getMin(), norm.getMax()));
            }
        }
    }

    /**
     * We evaluate the constraints imposed by the user and give the degree of usefulness
     * associated with the constraint with respect to the application.
     **/
    public void normalizedConstraints() {
        // Define variables
        double delta, qMin, qMinNorm, qMax, qMaxNorm;
        Double ref, refTop, nRef, nRefTop;

        for (Map.Entry<QoS, Constraint> entry : _softConstraints.entrySet()) {
            QoS k = entry.getKey();

            Constraint constraint = entry.getValue();
            MinMax minMax = _qMinMaxAggregated.get(k);
            qMin = minMax.getMin();
            qMax = minMax.getMax();

            Normalization norm = new Normalization(qMin, qMax);
            double nVal = norm.normalize(
                constraint.getRef(), k.getObjective().equals(ObjectiveFunction.MINIMIZE), NormalizedMethod.MIN_MAX
            );

            // Save constraint normalized
            _normalizedConstraints.put(k, new Constraint(constraint.getOperator(), nVal));
        }
    }

    /**
     * For each service, providersUtility keeps the fitness for
     */
    public void providersUtility() {
        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            Integer iService = e.getKey(), iGenotype = e.getValue();
            Service s = getService(iService);
            _providersUtility(iGenotype, s.getCandidates(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            Integer iGate = e.getKey(), iGenotype = e.getValue();
            Gate g = getGate(iGate);
            _providersUtility(iGenotype, g.getCandidates(), _channelQoS);
        }
    }

    private void _providersUtility(Integer iGenotype, List<Integer> candidates, List<QoS> kList) {
        // Get utilities
        Map<Integer, Double> v = _utilityProvider.getOrDefault(iGenotype, new HashMap<>());
        // Define variables
        double nVal, val, w, vProvider, delta, qMin, qMinNorm, qMax, qMaxNorm;

        for (Integer p : candidates) {
            Provider provider = getProvider(p);
            vProvider = 0.;

            for (QoS k : kList) {
                // Extract min-max and weight
                MinMax minMax = _qMinMax.get(iGenotype).get(k);
                qMin = minMax.getMin();
                qMax = minMax.getMax();
                delta = (qMax - qMin) / _nOfDegrees;
                w = _weights.get(k);

                val = switch (k) {
                    case LATENCY -> _latency.get(iGenotype).get(p);
                    case THROUGHPUT -> provider.getConnRange().getCapacity();
                    default -> provider.getAttributeValue(k);
                };

                if (k.getObjective().equals(ObjectiveFunction.MAXIMIZE)) {
                    qMinNorm = qMin - delta;
                    nVal = (val - qMinNorm) / (qMax - qMinNorm);
                } else {
                    qMaxNorm = qMax + delta;
                    nVal = (qMaxNorm - val) / (qMaxNorm - qMin);
                }

                vProvider += nVal * w;
            }

            // Save utilities
            v.put(p, vProvider);
        }

        // Save utilities
        _utilityProvider.put(iGenotype, v);
    }

    /**
     * Return all providers without the service assigned
     *
     * @param utilityProvider which is the list that has the application with the available providers for each server
     * @return the list without the services
     */
    public Map<Integer, Double> getProvidersUtility(Map<Pair<Service, Provider>, Double> utilityProvider) {
        Map<Integer, Double> solution = new LinkedHashMap<>();
        for (Service s : getServices()) {
            for (Integer i : s.getCandidates()) {
                if (!solution.containsKey(i)) {
                    solution.put(i, utilityProvider.get(new Pair<>(s, getProvider(i))));
                }
            }
        }
        return solution;
    }


    /**
     * Return the value of the fitness that must have each provider for each service
     *
     * @param bestGene that is the solution for each service.
     */

    public Map<Integer, Double> componentsUtilities(EvolutionResult<IntegerGene, Double> bestGene) {
        // First, clear previous calculations
        _componentRequiredUtility.clear();

        // Get best genome
        Genotype<IntegerGene> bestGenotype = bestGene.bestPhenotype().genotype();

        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            Integer iService = e.getKey(), iGenotype = e.getValue();
            Service s = getService(iService);

            // Get fitness for the service and last position
            Double cUtility = _cUtility(bestGenotype, iGenotype, true);

            // Save component fitness
            _componentRequiredUtility.put(iGenotype, cUtility);

            // Save provider fitness
            _providersUtility(iGenotype, s.getCandidates(), _qosList);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            Integer iGate = e.getKey(), iGenotype = e.getValue();
            Gate g = getGate(iGate);

            // Get fitness for the gate and last position
            Double cUtility = _cUtility(bestGenotype, iGenotype, false);

            // Save component fitness
            _componentRequiredUtility.put(iGenotype, cUtility);

            // Save provider fitness
            _providersUtility(iGenotype, g.getCandidates(), _channelQoS);
        }

        return _componentRequiredUtility;
    }

    private Double _cUtility(Genotype<IntegerGene> genotype, Integer iGenotype, Boolean isService) {
        // Obtaining the corresponding list...
        List<QoS> qosList = isService ? _qosList : _channelQoS;

        // Define component fitness
        double cUtility = 0.;

        // Prepare position of genome
        int gPosition, factorQoS = qosList.size();

        for (int initialQoSIndex = 0; initialQoSIndex < factorQoS; initialQoSIndex++) {
            // Get QoS attribute
            QoS k = qosList.get(initialQoSIndex);

            // Extract position
            gPosition = (iGenotype * factorQoS) + initialQoSIndex;

            // Extract allele for that position
            double allele = genotype.get(gPosition).get(0).allele();

            // Add to component fitness
            cUtility += (allele / _nOfDegrees) * getWeights().get(k);
        }

        // Return component's fitness and last position
        return cUtility;
    }

    private Double _cUtilityAlt(Genotype<IntegerGene> genotype, Integer iGenotype, Boolean isService) {
        // Obtaining the corresponding list...
        List<QoS> qosList = isService ? _qosList : _channelQoS;

        // Define component fitness
        double cUtility = 0.;

        // Prepare position of genome
        int gPosition, factorQoS = qosList.size();

        for (int initialQoSIndex = 0; initialQoSIndex < factorQoS; initialQoSIndex++) {
            // Get QoS attribute
            QoS k = qosList.get(initialQoSIndex);

            // Extract position
            gPosition = (iGenotype * factorQoS) + initialQoSIndex;

            // Extract allele for that position
            int allele = genotype.get(gPosition).get(0).allele();

            // Extract value of that allele (Utility's degree)
            double uDegree = getUtilityNormalizedMatrix().get(iGenotype).get(k).get(allele);

            // Add to component fitness
            cUtility += uDegree * getWeights().get(k);
        }

        // Return component's fitness and last position
        return cUtility;
    }

    public Map<Integer, Map<QoS, List<Double>>> getUtilityNormalizedMatrix() {
        return _utilityMatrixNorm;
    }

    public Map<Integer, Double> getComponentRequiredUtility() {
        return _componentRequiredUtility;
    }

    public Map<QoS, Constraint> getNormalizedConstraints() {
        return _normalizedConstraints;
    }

    public Map<Integer, Map<Integer, Double>> getUtilityProvider() {
        return _utilityProvider;
    }

    public int getNOfDegrees() {
        return _nOfDegrees;
    }

    public void printUtilityRequiredService() {
        for (Map.Entry<Integer, Double> e : _componentRequiredUtility.entrySet()) {
            Integer iGenotype = e.getKey();
            Double utility = e.getValue();

            System.out.printf("Component: %d -> Utility: %.3f %n", iGenotype, utility);
        }
    }

    public void printProvidersUtility() {
        for (Map.Entry<Integer, Map<Integer, Double>> e : _utilityProvider.entrySet()) {
            Integer iGenotype = e.getKey();
            Map<Integer, Double> values = e.getValue();

            for (Map.Entry<Integer, Double> v : values.entrySet()) {
                System.out.printf("(%d, %s) -> %s%n", iGenotype, v.getKey(), v.getValue());
            }
        }
    }

    public void setComposition(Map<Service, Integer> composition) {
        // Keeps the composition.
    }

    public void printUtilityNormalizedMatrix() {
        for (Map.Entry<Integer, Map<QoS, List<Double>>> e : _utilityMatrixNorm.entrySet()) {
            Integer iGenotype = e.getKey();
            Map<QoS, List<Double>> values = e.getValue();

            for (Map.Entry<QoS, List<Double>> v : values.entrySet()) {
                System.out.printf("(%d, %s) -> %s%n", iGenotype, v.getKey(), v.getValue());
            }
        }
    }
}
