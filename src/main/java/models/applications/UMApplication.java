package models.applications;


import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import io.jenetics.engine.EvolutionResult;
import models.auxiliary.Constraint;
import models.auxiliary.Latency;
import models.auxiliary.MinMax;
import models.auxiliary.Normalization;
import models.enums.ObjectiveFunction;
import models.enums.QoS;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static models.enums.QoS.LATENCY;


public class UMApplication extends Application {
    // Stores the min-max values of each service by looking at all providers and is stored for each quality of
    // service attribute.
    private final Map<Integer, Map<QoS, MinMax>> _qMinMax = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, Double>> _latency = new ConcurrentHashMap<>();

    /**
     * - The value that takes the application with the max-min values of each QoS Attribute
     * - Constraints converted to fitness constraints. This is the fitness that user will expect of the deployment
     */
    private final Map<QoS, MinMax> _qMinMaxAggregated = new HashMap<>();

    private final Map<Integer, Map<QoS, List<Double>>>
        _qDegreeMatrix = new LinkedHashMap<>(),
        _qDegreeMatrixNorm = new LinkedHashMap<>();

    /**
     * - The value that takes the application with the max-min values of each QoS Attribute
     * - Constraints converted to fitness constraints. This is the fitness that user will expect of the deployment
     */
    // Keeps the information of the ranges of the matrix
    private final Integer _nOfDegrees;

    // This is the fitness that each provider has
    private final Map<Integer, Map<Integer, Map<QoS, Double>>> _utilityProvider = new HashMap<>();

    public UMApplication(Application o) {
        this(o, new HashMap<>());
    }

    public UMApplication(Application o, Integer degrees) {
        this(o, new HashMap<>(), degrees);
    }

    public UMApplication(Application o, Map<QoS, Constraint> constraint) {
        this(o, constraint, o._nOfQoS);
    }

    public UMApplication(Application o, Map<QoS, Constraint> constraint, Integer degrees) {
        super(o);

        if (o._channelQoS.contains(LATENCY)) {
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
        // Extract Min & Max aggregated for the application
        qMinMaxAggregated();
        // Extract "fitness" from providers
        providersUtility();
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

            // Append this quality degree.
            vNorm.put(k, auxNorm);
            v.put(k, aux);
        }

        // Update dictionary
        _qDegreeMatrixNorm.put(iGenotype, vNorm);
        _qDegreeMatrix.put(iGenotype, v);
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
        Map<Integer, Map<QoS, Double>> v = _utilityProvider.getOrDefault(iGenotype, new HashMap<>());
        // Define variables
        double nVal, val, delta, qMin, qMinNorm, qMax, qMaxNorm;

        for (Integer p : candidates) {
            // Get provider
            Provider provider = getProvider(p);
            // Utility providers
            Map<QoS, Double> uProvider = new HashMap<>();

            for (QoS k : kList) {
                // Extract min-max and weight
                MinMax minMax = _qMinMax.get(iGenotype).get(k);
                qMin = minMax.getMin();
                qMax = minMax.getMax();
                delta = (qMax - qMin) / _nOfDegrees;

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

                uProvider.put(k, nVal);
            }

            // Save utilities
            v.put(p, uProvider);
        }

        // Save utilities
        _utilityProvider.put(iGenotype, v);
    }

    public Integer getNOfDegrees() {
        return _nOfDegrees;
    }

    public final Map<Integer, Map<QoS, List<Double>>> getQDegreeMatrixNorm() {
        return _qDegreeMatrixNorm;
    }

    public final Map<Integer, Map<QoS, List<Double>>> getQDegreeMatrix() {
        return _qDegreeMatrix;
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

    public Double getFitnessWithoutConstraints(Map<Integer, Provider> composition) {
        double fitness = 0., w, v, vNorm;

        // For each QoS attribute
        for (Map.Entry<QoS, Double> e : _weights.entrySet()) {
            // Extract information
            QoS k = e.getKey();
            w = e.getValue();

            v = switch (k) {
                case LATENCY -> composition.entrySet().parallelStream()
                    .mapToDouble(c -> c.getValue().getAttributeValue(k)).average().orElse(0.);
                case THROUGHPUT -> composition.entrySet().parallelStream()
                    .mapToDouble(c -> c.getValue().getAttributeValue(k)).min().orElse(0.);
                default -> _architecture.value(k, composition);
            };

            // Get normalization for this QoS attribute
            MinMax minMax = _qMinMaxAggregated.get(k);
            Normalization norm = new Normalization(minMax);
            boolean toMinimize = k.getObjective().equals(ObjectiveFunction.MINIMIZE);

            // Apply transform function to value
            vNorm = norm.normalize(v, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += vNorm * w;
        }

        return fitness;
    }

    public Double getFitnessWithConstraints(Map<Integer, Provider> composition) {
        // QoS constraints
        double fitness = 0., v, vNorm, w, softConstraintsFailed = 0.;

        // For each QoS attribute
        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            // Extract information
            QoS k = entry.getKey();
            w = entry.getValue();

            v = switch (k) {
                case LATENCY -> composition.entrySet().parallelStream()
                    .mapToDouble(c -> c.getValue().getAttributeValue(k)).average().orElse(0.);
                case THROUGHPUT -> composition.entrySet().parallelStream()
                    .mapToDouble(c -> c.getValue().getAttributeValue(k)).min().orElse(0.);
                default -> _architecture.value(k, composition);
            };

            // Getting constraints
            Constraint softConstraint = _softConstraints.get(k), hardConstraint = _hardConstraints.get(k);

            // If exists a hard constraint for this QoS, check if is valid or not. If isn't valid then return 0.
            if (hardConstraint != null && hardConstraint.isInvalid(v)) {
                return 0.;
            }

            // If exists a soft constraint for this QoS, then check it
            if (softConstraint != null && softConstraint.isInvalid(v)) {
                softConstraintsFailed++;
            }

            // Normalize value
            Normalization norm = _appNorm.get(k);
            boolean toMinimize = k.getObjective().equals(ObjectiveFunction.MINIMIZE);

            // Apply transform function to value
            vNorm = norm.normalize(v, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += vNorm * w;
        }

        // Add penalization by constraints failed
        double n = 1. - (softConstraintsFailed / Math.max(_softConstraints.size(), 1.));

        // f = (n * penalty) + ((1 - penalty) * f)
        fitness = (n * _softConstraintsW) + ((1 - _softConstraintsW) * fitness);

        return fitness;
    }

    public Map<Integer, Integer> getSimilarProviders(EvolutionResult<IntegerGene, Double> bestGene) {
        // Prepare composition map
        Map<Integer, Integer> composition = new ConcurrentHashMap<>();
        // Get best genome
        Genotype<IntegerGene> bestGenotype = bestGene.bestPhenotype().genotype();

        for (Map.Entry<Integer, Integer> e : getServicesToExplore().entrySet()) {
            Integer iGenotype = e.getValue();

            // Get fitness for the service and last position
            Integer simProvider = _similarProvider(bestGenotype, iGenotype, true);

            // Save more similar provider
            composition.put(iGenotype, simProvider);
        }

        for (Map.Entry<Integer, Integer> e : getGatesToExplore().entrySet()) {
            Integer iGenotype = e.getValue();

            // Get fitness for the service and last position
            Integer simProvider = _similarProvider(bestGenotype, iGenotype, false);

            // Save more similar provider
            composition.put(iGenotype, simProvider);
        }

        return composition;
    }

    private Integer _similarProvider(Genotype<IntegerGene> genotype, Integer iGenotype, Boolean isService) {
        // Obtaining the corresponding list...
        List<QoS> qosList = isService ? _qosList : _channelQoS;

        // Define best provider id and closest value
        int bestProvider = -1;
        double closestValue = Double.MAX_VALUE, currentClose;

        // Prepare position of genome
        int gPosition, factorQoS = qosList.size();

        // For each provider
        for (int p = 0; p < getProviders().size(); p++) {

            // Prepare closest value
            currentClose = 0.;

            for (int initialQoSIndex = 0; initialQoSIndex < factorQoS; initialQoSIndex++) {
                // Get QoS attribute
                QoS k = qosList.get(initialQoSIndex);

                // Extract position
                gPosition = (iGenotype * factorQoS) + initialQoSIndex;

                // Extract allele for that position
                int allele = genotype.get(gPosition).get(0).allele();

                // Obtain value representative of the cluster
                double v = _qDegreeMatrixNorm.get(iGenotype).get(k).get(allele);

                // Get value to compare
                double v2 = _utilityProvider.get(iGenotype).get(p).get(k);

                // Update closest
                currentClose += Math.abs(v - v2);
            }

            if (currentClose < closestValue) {
                closestValue = currentClose;
                bestProvider = p;
            }
        }

        // Return best provider
        return bestProvider;
    }
}
