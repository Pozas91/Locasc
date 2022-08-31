package models.applications;

import generators.Architectures;
import generators.Graphs;
import generators.Locations;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.auxiliary.*;
import models.enums.CONFIG;
import models.enums.NormalizedMethod;
import models.enums.ObjectiveFunction;
import models.enums.QoS;
import models.geo.Location;
import models.patterns.Architecture;
import models.patterns.BaseComponent;
import org.javatuples.Pair;
import utils.RunConf;
import utils.ToDebug;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// This class is called `Service` in papers that we've read.
public class Application implements Serializable {
    // Architecture defined for this application
    protected Architecture _architecture;

    /*
     * Associate a index of service with it position into genotype, see follow example:
     * - [0, 2, 1] genotype
     * - [0 -> 1, 1 -> 0, 2 -> 2] servicesToExplore
     *
     * Service 0 has provider 2 selected :> 0 -> 1 -> 2
     * Service 1 has provider 0 selected :> 1 -> 0 -> 0
     * Service 2 has provider 1 selected :> 2 -> 2 -> 1
     *
     * Key -> Index of service
     * Value -> Index of genotype (To gates is `nOfServices` + `n`)
     */
    protected Map<Integer, Integer> _servicesToExplore, _gatesToExplore;

    // Catalog of providers and services
    protected final List<Provider> _providers;
    protected final List<Service> _services;
    protected List<Gate> _gates;

    // Weights and constraints to calculate application's value
    protected final Map<QoS, Double> _weights;
    // Hard constraints are constraints that if any of them aren't satisfied, then f(x) = 0
    protected Map<QoS, Constraint> _softConstraints, _hardConstraints;

    // Normalizations for providers and normalizations for applications value.
    protected final Map<QoS, Normalization> _providersNorm, _appNorm;

    // Define constraints penalty
    protected Double _softConstraintsW;

    // Define method to normalize
    protected final NormalizedMethod _nMethod;

    // Define QoS attributes used
    protected final List<QoS> _qosList, _channelQoS, _providerQoS;

    protected final Integer _nOfQoS;

    // Latency variables
    protected Location _inputPoint = null, _outputPoint = null;
    protected Node _graph;

    /**
     * This is a map where key is the index of service and key is the position of the selected provider.
     * For example, value is 1, so this means that this index service will have selected the 1 index of the global app
     * providers.
     */
    protected ConcurrentMap<Long, ConcurrentMap<Integer, Integer>> _servicesComposition, _gatesComposition;

    public Application(
        Architecture architecture, List<Service> services, List<Provider> providers, Map<QoS, Double> weights,
        Map<QoS, Constraint> softConstraints, Double softConstraintsW, Map<QoS, Constraint> hardConstraints,
        NormalizedMethod nMethod, List<QoS> qosList
    ) {
        _architecture = architecture;
        _services = services;
        _providers = providers;
        _weights = new ConcurrentHashMap<>(weights);
        _softConstraints = softConstraints;
        _softConstraintsW = softConstraintsW;
        _hardConstraints = hardConstraints;

        // It is very important to have the elements of the QoS list ordered, because the channel-dependent attributes
        // must come before the others.
        _qosList = new ArrayList<>(qosList);
        Collections.sort(_qosList);

        _channelQoS = new ArrayList<>();
        _providerQoS = new ArrayList<>();
        _nOfQoS = qosList.size();

        // Divide between channel and provider QoS.
        for (QoS q : _qosList) {
            switch (q) {
                case THROUGHPUT, LATENCY -> _channelQoS.add(q);
                default -> _providerQoS.add(q);
            }
        }

        // To default all services are to explore
        _servicesToExplore = new ConcurrentHashMap<>(IntStream.range(0, _services.size())
            .parallel().mapToObj(i -> new AbstractMap.SimpleEntry<>(i, i))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));
        _gatesToExplore = new ConcurrentHashMap<>();

        // Dictionaries to save normalization to improve application execution
        _providersNorm = new ConcurrentHashMap<>();
        _appNorm = new ConcurrentHashMap<>();
        // Set method to normalize
        _nMethod = nMethod;

        // Initialize map of composition
        _servicesComposition = new ConcurrentHashMap<>();
        _gatesComposition = new ConcurrentHashMap<>();

        // If we want work with latency, need the latency's matrix.
        if (!_channelQoS.isEmpty()) {
            _inputPoint = Locations.get().get(0);
            _outputPoint = Locations.get().get(0);

            // Extract graph
            Node root = Graphs.get(this);
            _graph = root;

            // Prepare list of gates
            _gates = Arrays.asList(new Gate[Architectures.GATE_ID]);

            // Extract gates from graph, and set candidates
            IntStream.range(0, Architectures.GATE_ID).forEach(i -> {
                Gate g = new Gate(i);
                g.setApp(root.getApp());
                _gates.set(i, g);
            });
        } else {
            _gates = new ArrayList<>();
        }
    }

    public Application(
        Architecture architecture, List<Service> services, List<Provider> providers, Map<QoS, Double> weights,
        NormalizedMethod nMethod, List<QoS> qosList
    ) {
        this(
            architecture, services, providers, weights, new HashMap<>(), 0., new HashMap<>(), nMethod,
            qosList
        );
    }

    public Application(
        Architecture architecture, List<Service> services, List<Provider> providers, Map<QoS, Double> weights,
        List<QoS> qosList
    ) {
        this(
            architecture, services, providers, weights, new HashMap<>(), 0., new HashMap<>(),
            NormalizedMethod.MIN_MAX, qosList
        );
    }

    public Application(Application o) {
        // Create a copy for this app
        _architecture = o._architecture;

        // MARK: Create a new composition for the new application
        _servicesComposition = new ConcurrentHashMap<>();
        _gatesComposition = new ConcurrentHashMap<>();

        // MARK: Attributes that simply need a shallow copy
        _services = new ArrayList<>(o._services);
        _providers = new ArrayList<>(o._providers);
        _gates = new ArrayList<>(o._gates);
        _servicesToExplore = new ConcurrentHashMap<>(o._servicesToExplore);
        _gatesToExplore = new ConcurrentHashMap<>(o._gatesToExplore);
        _weights = new ConcurrentHashMap<>(o._weights);
        _appNorm = new ConcurrentHashMap<>(o._appNorm);
        _hardConstraints = new ConcurrentHashMap<>(o._hardConstraints);
        _softConstraints = new ConcurrentHashMap<>(o._softConstraints);

        // These attributes aren't modified during multi-threading executions
        _softConstraintsW = o._softConstraintsW;
        _providersNorm = o._providersNorm;
        _nMethod = o._nMethod;
        _qosList = o._qosList;
        _providerQoS = o._providerQoS;
        _channelQoS = o._channelQoS;
        _nOfQoS = o._nOfQoS;
        _inputPoint = o._inputPoint;
        _outputPoint = o._outputPoint;
        _graph = o._graph;
    }

    /**
     * Calculate and update the minimum and maximum app required to normalize QoS attributes and constraints.
     * <p>
     * We suppose that all single values are normalized between [0, 1] so we can create the higher (h) and the
     * lower (l) provider and use it to calculate global min-max pair of values using architecture.
     */
    public void updateAppNormalization() {
        Map<QoS, Double> hAttributes = new HashMap<>(), lAttributes = new HashMap<>();

        for (Map.Entry<QoS, Normalization> entry : _providersNorm.entrySet()) {
            hAttributes.put(entry.getKey(), entry.getValue().getMax());
            lAttributes.put(entry.getKey(), entry.getValue().getMin());
        }

        // Create higher and lower providers
        List<Provider> providers = List.of(
            new Provider("Higher", hAttributes),
            new Provider("Lower", lAttributes)
        );

        // For each qos attribute of our application
        for (QoS k : _providersNorm.keySet()) {
            // Define min-max pair
            MinMax minMax = new MinMax();

            if (k.equals(QoS.AVAILABILITY) || k.equals(QoS.RELIABILITY)) {
                minMax.setMinMax(0.);
                minMax.setMinMax(1.);
            } else {
                for (Provider provider : providers) {
                    // Add this provider
                    _providers.add(provider);
                    // Get last index for that provider
                    int lastProvider = _providers.size() - 1;
                    // Define services composition
                    List<Integer> composition = Arrays.asList(
                        new Integer[_servicesToExplore.size() + _gatesToExplore.size()]
                    );

                    // All services and gates have selected that provider
                    for (Map.Entry<Integer, Integer> e : _servicesToExplore.entrySet()) {
                        Integer iService = e.getKey(), iGenotype = e.getValue();
                        Service s = getService(iService);
                        s.addCandidate(lastProvider);
                        composition.set(iGenotype, s.getCandidates().size() - 1);
                    }

                    for (Map.Entry<Integer, Integer> e : _gatesToExplore.entrySet()) {
                        Integer iGate = e.getKey(), iGenotype = e.getValue();
                        Gate g = getGate(iGate);
                        g.addCandidate(lastProvider);
                        composition.set(iGenotype, g.getCandidates().size() - 1);
                    }

                    double val = _architecture.value(this, k, composition);

                    // Get min-max value for a architecture
                    minMax.setMinMax(val);

                    // Undo previous process
                    _providers.remove(provider);

                    // Remove selected provider from all services and gates
                    for (Map.Entry<Integer, Integer> e : _servicesToExplore.entrySet()) {
                        Integer iService = e.getKey();
                        Service s = getService(iService);
                        s.removeCandidate(lastProvider);
                    }

                    for (Map.Entry<Integer, Integer> e : _gatesToExplore.entrySet()) {
                        Integer iGate = e.getKey();
                        Gate g = getGate(iGate);
                        g.removeCandidate(lastProvider);
                    }
                }
            }

            // Update app-normalization
            _appNorm.put(k, new Normalization(minMax.getMin(), minMax.getMax()));
        }

        // Clean fake compositions
        _servicesComposition.clear();
        _gatesComposition.clear();

        // If qos list contains latency, calculate latency normalization for the application
        if (_channelQoS.contains(QoS.LATENCY)) {
            Pair<Double, Double> minMax = Latency.minMax(_graph);
            _appNorm.put(QoS.LATENCY, new Normalization(minMax.getValue0(), minMax.getValue1()));
        }

        if (_channelQoS.contains(QoS.THROUGHPUT)) {
            Pair<Double, Double> minMax = Throughput.minMax(_graph);
            _appNorm.put(QoS.THROUGHPUT, new Normalization(minMax.getValue0(), minMax.getValue1()));
        }
    }

    public Integer getNOfQoS() {
        return _nOfQoS;
    }

    /**
     * Get the list of services for this architecture
     *
     * @return A list of services
     */
    public List<Service> getServices() {
        return _services;
    }

    public List<Provider> getProviders() {
        return _providers;
    }

    public Provider getProvider(Integer i) {
        return _providers.get(i);
    }

    public Map<QoS, Double> getWeights() {
        return _weights;
    }

    public Map<QoS, Constraint> getSoftConstraints() {
        return _softConstraints;
    }

    public void putSoftConstraint(QoS qos, Constraint constraint) {
        _softConstraints.put(qos, constraint);
    }

    public Map<QoS, Normalization> getAppNorm() {
        return _appNorm;
    }

    public Map<QoS, Normalization> getProvidersNorm() {
        return _providersNorm;
    }

    public Architecture getArchitecture() {
        return _architecture;
    }

    public void setArchitecture(Architecture architecture) {
        _architecture = architecture;
    }

    public void setSoftConstraintsW(Double weight) {
        _softConstraintsW = weight;
    }

    public void setGraph(Node root) {
        _graph = root;
    }

    public Node getGraph() {
        return _graph;
    }

    public void cleanServicesToExplore() {
        _servicesToExplore.clear();
    }

    public Integer getServiceIProvider(Long threadId, Integer iService) {
        Integer iProvider;

        if (_servicesComposition.containsKey(threadId)) {
            iProvider = _servicesComposition.get(threadId).getOrDefault(iService, 0);
        } else {
            iProvider = 0;
        }

        return iProvider;
    }

    public Integer getGateIProvider(Long threadId, Integer iGate) {
        Integer iProvider;

        if (_gatesComposition.containsKey((threadId))) {
            iProvider = _gatesComposition.get(threadId).getOrDefault(iGate, 0);
        } else {
            iProvider = 0;
        }

        return iProvider;
    }

    private Double getFitnessWithConstraints(Long threadId) {
        // QoS constraints
        double fitness = 0., val, weight, softConstraintsFailed = 0.;

        // For each QoS attribute
        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            // Extract information
            QoS qos = entry.getKey();
            weight = entry.getValue();

            val = switch (qos) {
                case LATENCY -> Latency.get(_graph, threadId);
                case THROUGHPUT -> Throughput.get(_graph, threadId);
                default -> _architecture.value(this, qos, threadId);
            };

            // Getting constraints
            Constraint softConstraint = _softConstraints.get(qos), hardConstraint = _hardConstraints.get(qos);

            // If exists a hard constraint for this QoS, check if is valid or not. If isn't valid then return 0.
            if (hardConstraint != null && hardConstraint.isInvalid(val)) {
                return 0.;
            }

            // If exists a soft constraint for this QoS, then check it
            if (softConstraint != null && softConstraint.isInvalid(val)) {
                softConstraintsFailed++;
            }

            // Normalize value
            Normalization norm = _appNorm.get(qos);
            boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;

            // Apply transform function to value
            double nVal = norm.normalize(val, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += nVal * weight;
        }

        // Add penalization by constraints failed
        double n = (1. - (softConstraintsFailed / Math.max(_softConstraints.size(), 1.)));

        // f = (n * penalty) + ((1 - penalty) * f)
        fitness = (n * _softConstraintsW) + ((1 - _softConstraintsW) * fitness);

        return fitness;
    }

    private Double getFitnessWithoutConstraints(Long threadId) {
        double fitness = 0., weight, val;

        // For each QoS attribute
        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            // Extract information
            QoS qos = entry.getKey();
            weight = entry.getValue();

            val = switch (qos) {
                case LATENCY -> Latency.get(_graph, threadId);
                case THROUGHPUT -> Throughput.get(_graph, threadId);
                default -> _architecture.value(this, qos, threadId);
            };

            // Get normalization for this attribute
            Normalization norm = _appNorm.get(qos);
            boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;

            // Apply transform function to value
            double nVal = norm.normalize(val, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += nVal * weight;
        }

        return fitness;
    }

    private Double getFitnessWithoutConstraints(List<Integer> composition) {
        double fitness = 0., weight, val;

        // For each QoS attribute
        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            // Extract information
            QoS qos = entry.getKey();
            weight = entry.getValue();

            // Extract value of this architecture
            val = switch (qos) {
                case LATENCY -> Latency.get(_graph, composition);
                case THROUGHPUT -> Throughput.get(_graph, composition);
                default -> _architecture.value(this, qos, composition);
            };

            // Get normalization for this attribute
            Normalization norm = _appNorm.get(qos);
            boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;

            // Apply transform function to value
            double nVal = norm.normalize(val, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += nVal * weight;
        }

        return fitness;
    }

    private Double getFitnessWithConstraints(List<Integer> composition) {
        // QoS constraints
        double fitness = 0., val, weight, softConstraintsFailed = 0.;

        // For each QoS attribute
        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            // Extract information
            QoS qos = entry.getKey();
            weight = entry.getValue();

            // Extract value of this architecture
            val = switch (qos) {
                case LATENCY -> Latency.get(_graph, composition);
                case THROUGHPUT -> Throughput.get(_graph, composition);
                default -> _architecture.value(this, qos, composition);
            };

            // Getting constraints
            Constraint softConstraint = _softConstraints.get(qos), hardConstraint = _hardConstraints.get(qos);

            // If exists a hard constraint for this QoS, check if is valid or not. If isn't valid then return 0.
            if (hardConstraint != null && hardConstraint.isInvalid(val)) {
                return 0.;
            }

            // If exists a soft constraint for this QoS, then check it
            if (softConstraint != null && softConstraint.isInvalid(val)) {
                softConstraintsFailed++;
            }

            // Normalize value
            Normalization norm = _appNorm.get(qos);
            boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;

            // Apply transform function to value
            double nVal = norm.normalize(val, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += nVal * weight;
        }

        // Add penalization by constraints failed
        double n = 1. - (softConstraintsFailed / Math.max(_softConstraints.size(), 1.));

        // f = (n * penalty) + ((1 - penalty) * f)
        fitness = (n * _softConstraintsW) + ((1 - _softConstraintsW) * fitness);

        return fitness;
    }

    @Override
    public String toString() {
        return String.format("Application{%n architecture: %s,%n services: %s,%n weights: %s%n}", _architecture, _services, _weights);
    }

    // MARK: This function must be synchronized?
    private synchronized void setGateComposition(Long threadId, Integer iGate, Integer iProviderSelected) {
        if (_gatesComposition.containsKey(threadId)) {
            _gatesComposition.get(threadId).put(iGate, iProviderSelected);
        } else {
            ConcurrentMap<Integer, Integer> subComposition = new ConcurrentHashMap<>();
            subComposition.put(iGate, iProviderSelected);
            _gatesComposition.put(threadId, subComposition);
        }
    }

    // MARK: This function must be synchronized?
    public synchronized void setServiceComposition(Long threadId, Integer iService, Integer iProviderSelected) {
        if (_servicesComposition.containsKey(threadId)) {
            _servicesComposition.get(threadId).put(iService, iProviderSelected);
        } else {
            ConcurrentMap<Integer, Integer> subComposition = new ConcurrentHashMap<>();
            subComposition.put(iService, iProviderSelected);
            _servicesComposition.put(threadId, subComposition);
        }
    }

    public Application copy() {
        return new Application(this);
    }

    /**
     * Overload copy function modifying selected providers for the indicates in genotype.
     *
     * @param genotype List of genotype
     * @return Copy of this application where each service has selected the provider indicated
     */
    public Application copy(List<Integer> genotype) {
        long threadId = Thread.currentThread().getId();

        for (Map.Entry<Integer, Integer> e : _servicesToExplore.entrySet()) {
            int iService = e.getKey(), iGenotype = e.getValue();
            int iProviderSelected = genotype.get(iGenotype);
            setServiceComposition(threadId, iService, iProviderSelected);
        }

        if (genotype.size() == _servicesToExplore.size() + _gatesToExplore.size()) {
            for (Map.Entry<Integer, Integer> e : _gatesToExplore.entrySet()) {
                int iGate = e.getKey(), iGenotype = e.getValue();
                int iProviderSelected = genotype.get(iGenotype);
                setGateComposition(threadId, iGate, iProviderSelected);
            }
        }

        return this;
    }

    /**
     * Easier method where we pass a genotype (List of integer genes) and set each local provider with each service:
     * For example, we suppose that receive next genotype: [[1], [2], [5], [3], [6]]
     * We convert previous list of lists into a flat list [1, 2, 5, 3, 6]
     *
     * @param gt Genotype
     * @return A copy of this application with the selected providers
     */
    public Application copy(Genotype<IntegerGene> gt) {
        List<Integer> genotype = new ArrayList<>();

        for (int i = 0; i < gt.length(); i++) {
            genotype.add(gt.get(i).gene().allele());
        }

        return copy(genotype);
    }

    public Pair<Application, List<Integer>> copyPair(Genotype<IntegerGene> gt) {
        List<Integer> genotype = new ArrayList<>();

        for (int i = 0; i < gt.length(); i++) {
            genotype.add(gt.get(i).gene().allele());
        }

        return new Pair<>(this, genotype);
    }

    /**
     * Copy an application from a composition is more complicated, because we need to do reverse engineering, search the
     * index for the real provider indicated in the composition.
     *
     * @param composition A map where keys are services indexes and values are global providers positions
     * @return An application copy with each service with the local provider selected
     */
    public Application copy(Map<Integer, Integer> composition) {
        List<Integer> genotype = Arrays.asList(new Integer[composition.size()]);

        for (Map.Entry<Integer, Integer> e : composition.entrySet()) {
            genotype.set(e.getKey(), e.getValue());
        }

        return copy(genotype);
    }

    public void updateProvidersNormalization() {
        Map<QoS, MinMax> providersMinMax = new HashMap<>();

        // Get min and max attributes between all providers by QoS attribute
        for (Provider p : _providers) {
            // For each attribute
            for (QoS qos : _providerQoS) {
                // Get value
                Double value = p.getAttributeValue(qos);
                // Update min-max value pair
                MinMax minMax = providersMinMax.getOrDefault(qos, new MinMax());
                minMax.setMinMax(value);
                providersMinMax.put(qos, minMax);
            }
        }

        // Get global values for normalize
        for (QoS qos : _providerQoS) {
            MinMax minMax = providersMinMax.get(qos);

            // Apply transform function
            Normalization norm = new Normalization(minMax.getMin(), minMax.getMax());

            _providersNorm.put(qos, norm);
            boolean toMinimize = qos.getObjective().equals(ObjectiveFunction.MINIMIZE);

            for (Provider p : _providers) {
                p.setNormalizedValue(qos, norm.normalize(p.getAttributeValue(qos), toMinimize, _nMethod));
            }
        }
    }

    public Service getService(Integer i) {
        return _services.get(i);
    }

    public Provider convertToProvider(Long threadId) {
        // Copy any provider to update its attributes
        Map<QoS, Double> attributes = new HashMap<>();
        Provider p = new Provider("F_P", attributes);

        // Add application's value for each attribute
        for (QoS qos : _providerQoS) {
            p.getAttributes().put(qos, _architecture.value(this, qos, threadId));
            p.getNormalized().put(qos, _architecture.valueN(this, qos, threadId));
        }

        return p;
    }

    public Map<Integer, Integer> getServicesToExplore() {
        return _servicesToExplore;
    }

    /**
     * Returns the position or index of the genotype
     *
     * @param iService
     * @return
     */
    public Integer getServiceToExplore(Integer iService) {
        return _servicesToExplore.get(iService);
    }

    public Map<Integer, Integer> getGatesToExplore() {
        return _gatesToExplore;
    }

    public Map<QoS, Constraint> getHardConstraints() {
        return _hardConstraints;
    }

    public void putHardConstraints(QoS key, Constraint value) {
        _hardConstraints.put(key, value);
    }

    public void putWeights(QoS key, Double value) {
        _weights.put(key, value);
    }

    /**
     * This method will traverse the graph of nodes, and will assign as candidate providers to each gate the union of
     * providers of the previous and following services.
     */
    public void setGatesProviders() {
        setGatesProviders(this, _graph, _graph.getNext().get(0));
    }

    private void setGatesProviders(Application app, Node father, Node current) {
        if (current.getGateID() >= 0) {
            // Get providers (A U B)
            Set<Integer> aProviders = father.getProvidersIndex(app);
            Set<Integer> bProviders = current.getProvidersIndex(app);

            // C = A U B
            Set<Integer> union = new HashSet<>(aProviders);
            union.addAll(bProviders);

            // Set C into current gate
            Gate gate = getGate(current.getGateID());
            gate.addCandidates(new ArrayList<>(union));
        }

        current.getNext().forEach(n -> setGatesProviders(app, current, n));
    }

    public Pair<Pair<Integer, Double>, Pair<Integer, Double>> estimateBestAndWorstProviders() {

        Map<Integer, Double> providersEstimations = IntStream.range(0, getProviders().size())
            .parallel().mapToObj(x -> {
                Application copy = copy();
                copy._architecture.getIndexServices().parallelStream().forEach(
                    iService -> copy.setServiceComposition(0L, iService.getIService(), x)
                );
                return new AbstractMap.SimpleEntry<>(x, copy.fitness());
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Get the best provider
        Map.Entry<Integer, Double> bestProviderData = providersEstimations.entrySet()
            .parallelStream().max(Map.Entry.comparingByValue())
            .orElseThrow(NoSuchElementException::new);
        // Get the worst provider
        Map.Entry<Integer, Double> worstProviderData = providersEstimations.entrySet()
            .parallelStream().min(Map.Entry.comparingByValue())
            .orElseThrow(NoSuchElementException::new);

        return new Pair<>(
            new Pair<>(bestProviderData.getKey(), bestProviderData.getValue()),
            new Pair<>(worstProviderData.getKey(), worstProviderData.getValue())
        );
    }

    /**
     * Create a copy of the original problem with a new architecture given
     *
     * @param architectureToApply Part of parent architecture of this original application
     * @return A new application which is a sub-problem of original application
     */
    public Application getSubProblem(Architecture architectureToApply) {
        // 1. Copy this sub-problem
        Application subProblem = copy();

        // 2. Define map of services to explore in the sub-problem
        Map<Integer, Integer> servicesToExplore = new ConcurrentHashMap<>();
        List<BaseComponent> baseComponents = architectureToApply.getBaseComponents();

        // 3. Search each position of genotype which service represent
        for (int i = 0; i < baseComponents.size(); i++) {
            // Get base component
            BaseComponent baseComponent = baseComponents.get(i);
            // Update services to explore map
            servicesToExplore.put(baseComponent.getIService(), i);
        }

        // 4. Updating services to explore and architecture
        subProblem._servicesToExplore = servicesToExplore;
        subProblem.setArchitecture(architectureToApply);

        return subProblem;
    }

    /**
     * Calculate fitness function for an application
     *
     * @return A fitness function value
     */
    public Double fitness() {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        // If we haven't constraints, then calculate fitness without constraints penalty
        double fitness = (_softConstraints.isEmpty() && _hardConstraints.isEmpty())
            ? getFitnessWithoutConstraints(threadId)
            : getFitnessWithConstraints(threadId);

        if (RunConf.instance().getBoolean(CONFIG.EVOLUTION)) {
            ToDebug.getInstance().addCheckpoint(fitness, System.currentTimeMillis());
        }

        return fitness;
    }

    public Pair<Double, Double> getBounds() {
        return new Pair<>(getWorstFitness(), getBestFitness());
    }

    public Double getWorstFitness() {
        double fitness = 0., val;
        int softConstraintsFailed = 0;

        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            QoS qos = entry.getKey();
            double weight = entry.getValue();
            Constraint softConstraint = _softConstraints.get(qos), hardConstraint = _hardConstraints.get(qos);

            // Get normalization and check if the value to minimize?
            Normalization norm = _appNorm.get(qos);
            boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;

            // Get value
            val = toMinimize ? norm.getMax() : norm.getMin();

            if (hardConstraint != null && hardConstraint.isInvalid(val)) {
                return 0.;
            }

            if (softConstraint != null && softConstraint.isInvalid(val)) {
                softConstraintsFailed++;
            }

            // Normalize value
            double nVal = norm.normalize(val, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += nVal * weight;
        }

        // Add penalization by constraints failed
        int n = (1 - (softConstraintsFailed / Math.max(_softConstraints.size(), 1)));

        // f = (n * penalty) + ((1 - penalty) * f)
        fitness = (n * _softConstraintsW) + ((1 - _softConstraintsW) * fitness);

        return fitness;
    }

    public Double getBestFitness() {
        double fitness = 0., val;
        int softConstraintsFailed = 0;

        for (Map.Entry<QoS, Double> entry : _weights.entrySet()) {
            QoS qos = entry.getKey();
            double weight = entry.getValue();
            Constraint softConstraint = _softConstraints.get(qos), hardConstraint = _hardConstraints.get(qos);

            // Get normalization and check if the value to minimize?
            Normalization norm = _appNorm.get(qos);
            boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;

            // Get value
            val = toMinimize ? norm.getMin() : norm.getMax();

            if (hardConstraint != null && hardConstraint.isInvalid(val)) {
                return 0.;
            }

            if (softConstraint != null && softConstraint.isInvalid(val)) {
                softConstraintsFailed++;
            }

            // Normalize value
            double nVal = norm.normalize(val, toMinimize, _nMethod);

            // Accumulate this value multiply by it weight
            fitness += nVal * weight;
        }

        // Add penalization by constraints failed
        int n = (1 - (softConstraintsFailed / Math.max(_softConstraints.size(), 1)));

        // f = (n * penalty) + ((1 - penalty) * f)
        fitness = (n * _softConstraintsW) + ((1 - _softConstraintsW) * fitness);

        return fitness;
    }

    public Integer getQoSSize() {
        return _nOfQoS;
    }

    public List<QoS> getChannelQoS() {
        return _channelQoS;
    }

    public List<QoS> getProviderQoS() {
        return _providerQoS;
    }

    public List<QoS> getQoSList() {
        return _qosList;
    }

    public Double fitness(Map<Integer, Integer> composition) {
        Application copy = copy(composition);
        return copy.fitness();
    }

    public Double fitness(List<Integer> genotype) {
        Application copy = copy(genotype);
        return copy.fitness();
    }

    public Location getInputPoint() {
        return _inputPoint;
    }

    public Location getOutputPoint() {
        return _outputPoint;
    }

    public void setGates(List<Gate> gates) {
        _gates = gates;
    }

    public List<Gate> getGates() {
        return _gates;
    }

    public Gate getGate(Integer id) {
        return _gates.get(id);
    }

    public void updateGatesToExplore() {
        int offset = _servicesToExplore.size();

        _gatesToExplore = new ConcurrentHashMap<>(IntStream.range(0, _gates.size())
            .parallel().mapToObj(i -> new AbstractMap.SimpleEntry<>(i, i + offset))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));
    }

    public NormalizedMethod getMethod() {
        return _nMethod;
    }

    public Double getSoftConstraintsW() {
        return _softConstraintsW;
    }

    public void setServicesToExplore(ConcurrentMap<Integer, Integer> servicesToExplore) {
        _servicesToExplore = servicesToExplore;
    }

    public void setSoftConstraints(Map<QoS, Constraint> softConstraints) {
        _softConstraints = softConstraints;
    }

    public static Double fitnessPair(Pair<Application, List<Integer>> pair) {
        Application app = pair.getValue0();
        List<Integer> composition = pair.getValue1();

        // If we haven't constraints, then calculate fitness without constraints penalty
        double fitness = (app.getSoftConstraints().isEmpty() && app.getHardConstraints().isEmpty())
            ? app.getFitnessWithoutConstraints(composition)
            : app.getFitnessWithConstraints(composition);

        if (RunConf.instance().getBoolean(CONFIG.EVOLUTION)) {
            ToDebug.getInstance().addCheckpoint(fitness, System.currentTimeMillis());
        }

        return fitness;
    }
}
