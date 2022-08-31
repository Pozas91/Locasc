package resolvers;

import executions.App;
import models.applications.Application;
import models.applications.Gate;
import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.DistanceMatrix;
import models.auxiliary.MinMax;
import models.auxiliary.Node;
import models.auxiliary.Normalization;
import models.enums.Header;
import models.enums.NormalizedMethod;
import models.enums.ObjectiveFunction;
import models.enums.QoS;
import models.geo.Geo;
import models.geo.Location;
import models.patterns.IndexService;
import org.javatuples.Pair;
import utils.Composition;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Express {
    private static final Map<Integer, Map<Integer, Double>> _LATENCY = new ConcurrentHashMap<>();

    public static void resolve(Application app, Map<Header, List<Object>> data) {
        // 0. Initial time counter
        Instant startInstant = Instant.now();

        // 1. Calculate latency if is necessary
        if (app.getQoSList().contains(QoS.LATENCY)) {
            extractLatency(app);
        }

        // 2. Get composition
        List<Integer> composition = Composition.toList(getHighComposition(app));
        long executionTime = Duration.between(startInstant, Instant.now()).toMillis();

        // 3. Calculate fitness
        double fitness = Application.fitnessPair(new Pair<>(app, composition));

        // 4. Save only data needed
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.PRE_CALCULATION_TIME, 0),
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, executionTime),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, app.getProviders().size()),
            new AbstractMap.SimpleEntry<>(Header.SERVICES, app.getServices().size()),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, composition),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, 1)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static void extractLatency(Application app) {
        // Clean previous latency values
        _LATENCY.clear();
        // Get root
        Node root = app.getGraph();
        // Filling latency dictionary
        extractLatency(app, root, root.getNext(), new HashSet<>());
    }

    private static void extractLatency(Application app, Node current, List<Node> next, Set<Integer> visited) {
        if (!next.isEmpty()) {
            // For each father's provider
            if (current.getLocation() == null) {
                Integer iGenotype;
                List<Integer> candidates;

                if (current.getGateID() >= 0) {
                    Integer iGate = current.getGateID();
                    iGenotype = app.getGatesToExplore().get(iGate);
                    candidates = app.getGate(iGate).getCandidates();
                } else if (current.getComponent() != null) {
                    Integer iService = ((IndexService) current.getComponent()).getIService();
                    iGenotype = app.getServicesToExplore().get(iService);
                    candidates = app.getService(iService).getCandidates();
                } else {
                    throw new RuntimeException("Type of node doesn't recognise, please check it.");
                }

                if (visited.contains(iGenotype)) {
                    // Avoid revisit same nodes.
                    return;
                } else {
                    // Mark this service as visited
                    visited.add(iGenotype);

                    // Prepare map to save information
                    _LATENCY.put(iGenotype, new ConcurrentHashMap<>());
                }

                List<Location> locations = next.parallelStream()
                    .map(Node::getLocations)
                    .flatMap(Collection::parallelStream)
                    .collect(Collectors.toList());

                // Get list of providers for parent service
                for (Integer p : candidates) {
                    Location lProvider = app.getProvider(p).getLocation();

                    double lat = locations.parallelStream()
                        .mapToDouble(cLoc -> {
                            double distance = DistanceMatrix.get().distance(cLoc, lProvider);
                            return Geo.latency(distance);
                        })
                        .average().orElse(0);

                    // Add latency value in this provider
                    _LATENCY.get(iGenotype).put(p, lat);
                }

                next.forEach(child -> extractLatency(app, child, child.getNext(), visited));
            } else {
                current.getNext().forEach(p -> extractLatency(app, p, p.getNext(), visited));
            }
        }
    }

    private static Map<Integer, Integer> getHighComposition(Application app) {
        Map<Integer, Integer> composition = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : app.getServicesToExplore().entrySet()) {
            // Extract indexes
            int iService = entry.getKey(), iGenotype = entry.getValue();

            // Extract service
            Service s = app.getService(iService);

            // Get best provider
            Integer iBestProvider = getIBestProvider(app, iGenotype, s.getCandidates());

            // Set service with best provider
            composition.put(iGenotype, iBestProvider);
        }

        for (Map.Entry<Integer, Integer> entry : app.getGatesToExplore().entrySet()) {
            // Extract indexes
            int iGate = entry.getKey(), iGenotype = entry.getValue();

            // Extract service
            Gate g = app.getGate(iGate);

            // Get best provider
            Integer iBestProvider = getIBestProvider(app, iGenotype, g.getCandidates());

            // Set service with best provider
            composition.put(iGenotype, iBestProvider);
        }

        return composition;
    }

    private static Integer getIBestProvider(Application app, Integer iGenotype, List<Integer> candidates) {
        // Get normalization per component
        Map<QoS, Normalization> componentNorm = getNormPerComponent(app, iGenotype);

        double vBestProvider = Double.MIN_NORMAL;
        int iBestProvider = -1, iProvider = 0;

        // For each candidate for that service
        for (Integer candidate : candidates) {
            Provider p = app.getProvider(candidate);
            double value = 0;

            // For each weight
            for (QoS k : app.getQoSList()) {
                double weight = app.getWeights().get(k);

                // Get normalization for this attribute
                Normalization norm = componentNorm.get(k);
                boolean toMinimize = k.getObjective() == ObjectiveFunction.MINIMIZE;

                double v = switch (k) {
                    case LATENCY -> _LATENCY.get(iGenotype).get(candidate);
                    case THROUGHPUT -> p.getConnRange().getCapacity();
                    default -> p.getAttributeValue(k);
                };

                // Apply transform function to value
                double nValue = norm.normalize(v, toMinimize, NormalizedMethod.MIN_MAX);

                // Accumulate this value multiply by it weight
                value += nValue * weight;
            }

            if (value > vBestProvider) {
                vBestProvider = value;
                iBestProvider = iProvider;
            }

            // Increment provider index
            iProvider++;
        }

        return iBestProvider;
    }

    private static Map<QoS, Normalization> getNormPerComponent(Application app, Integer iGenotype) {
        Map<QoS, Normalization> componentNorm = new HashMap<>();

        for (QoS k : app.getQoSList()) {
            switch (k) {
                case COST, RESPONSE_TIME, AVAILABILITY, RELIABILITY ->
                    componentNorm.put(k, app.getProvidersNorm().get(k));
                case THROUGHPUT -> componentNorm.put(k, app.getAppNorm().get(k));
                case LATENCY -> {
                    MinMax minMax = new MinMax();

                    _LATENCY.get(iGenotype).entrySet().parallelStream()
                        .mapToDouble(Map.Entry::getValue)
                        .forEach(minMax::setMinMax);

                    componentNorm.put(k, new Normalization(minMax.getMin(), minMax.getMax()));
                }
            }
        }

        return componentNorm;
    }
}
