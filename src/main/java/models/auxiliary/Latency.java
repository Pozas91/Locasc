package models.auxiliary;

import models.applications.Application;
import models.applications.Gate;
import models.geo.Geo;
import models.geo.Location;
import models.patterns.IndexService;
import org.javatuples.Pair;
import utils.Sets;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Latency {
    public static Pair<Double, Double> minMax(Node x) {
        return minMax(x, new ConcurrentHashMap<>());
    }

    public static Double get(Node x, Long threadId) {
        return get(x, threadId, new ConcurrentHashMap<>());
    }

    public static Double get(Node x, List<Integer> composition) {
        return get(x, composition, new ConcurrentHashMap<>());
    }

    private static Pair<Double, Double> minMax(Node x, ConcurrentMap<Node, Pair<Double, Double>> cache) {
        if (x.getNext().isEmpty()) {
            return new Pair<>(0., 0.);
        } else {
            Stream<Pair<Double, Double>> stream = IntStream.range(0, x.getNext().size())
                .parallel().mapToObj(i -> {
                    // Get next node
                    Node n = x.getNext(i);
                    double dMin = Double.MAX_VALUE, dMax = Double.MIN_VALUE, f = x.getFactor(i), l;
                    // Define f(n)
                    Pair<Double, Double> f_n;

                    // There are 3 types of nodes:
                    // 1. Initial and Last -> _location != null
                    // 2. IndexService -> _component != null
                    // 3. GateID -> _gateID >= 0

                    Set<Location> sX = getLocations(x), sN = getLocations(n);

                    for (Location lX : sX) {
                        for (Location lN : sN) {
                            l = Geo.latency(DistanceMatrix.get().distance(lX, lN));
                            dMin = Math.min(dMin, l);
                            dMax = Math.max(dMax, l);
                        }
                    }

                    // Get f(n)
                    if (cache.containsKey(n)) {
                        f_n = cache.get(n);
                    } else {
                        f_n = minMax(n, cache);
                        cache.put(n, f_n);
                    }

                    return new Pair<>(
                        f * (dMin + f_n.getValue0()),
                        f * (dMax + f_n.getValue1())
                    );
                });

            Pair<Double, Double> minMax;

            if (x.getParallels()) {
                minMax = stream.reduce(new Pair<>(0., 0.), (t1, t2) -> new Pair<>(
                    Math.max(t1.getValue0(), t2.getValue0()), Math.max(t1.getValue1(), t2.getValue1())
                ));
            } else {
                minMax = stream.reduce(new Pair<>(0., 0.), (t1, t2) -> new Pair<>(
                    t1.getValue0() + t2.getValue0(), t1.getValue1() + t2.getValue1()
                ));
            }

            return minMax;
        }
    }

    private static Double get(Node x, Long threadId, ConcurrentMap<Node, Double> cache) {
        if (x.getNext().isEmpty()) {
            return 0.;
        } else {
            DoubleStream s = IntStream.range(0, x.getNext().size())
                .parallel()
                .mapToDouble(i -> {
                    Node n = x.getNext(i);

                    // Define f(n)
                    double f_n, f = x.getFactor(i), d = DistanceMatrix.get().distance(
                        x.getLocation(x.getApp(), threadId), n.getLocation(n.getApp(), threadId)
                    );

                    // Get latency
                    double l = Geo.latency(d);

                    // Get f(n)
                    if (cache.containsKey(n)) {
                        f_n = cache.get(n);
                    } else {
                        f_n = get(n, threadId, cache);
                        cache.put(n, f_n);
                    }

                    return f * (l + f_n);
                });

            if (x.getParallels()) {
                return s.max().orElse(0.);
            } else {
                return s.sum();
            }
        }
    }

    private static Double get(Node x, List<Integer> composition, ConcurrentMap<Node, Double> cache) {
        if (x.getNext().isEmpty()) {
            return 0.;
        } else {
            DoubleStream s = IntStream.range(0, x.getNext().size())
                .parallel()
                .mapToDouble(i -> {
                    Node n = x.getNext(i);

                    // Define f(n)
                    double f_n, f = x.getFactor(i), d = DistanceMatrix.get().distance(
                        x.getLocation(composition), n.getLocation(composition)
                    );

                    // Get latency
                    double l = Geo.latency(d);

                    // Get f(n)
                    if (cache.containsKey(n)) {
                        f_n = cache.get(n);
                    } else {
                        f_n = get(n, composition, cache);
                        cache.put(n, f_n);
                    }

                    return f * (l + f_n);
                });

            if (x.getParallels()) {
                return s.max().orElse(0.);
            } else {
                return s.sum();
            }
        }
    }

    private static Set<Location> getLocations(Node x) {
        if (x.getLocation() != null) {
            return Sets.of(x.getLocation());
        } else if (x.getComponent() != null) {
            // Extract index service
            IndexService s = (IndexService) x.getComponent();
            // Get all locations
            return s.getLocations(x.getApp());
        } else if (x.getGateID() >= 0) {
            // Extract gate
            Gate g = x.getApp().getGate(x.getGateID());
            // Get all locations
            return g.getLocations();
        } else {
            throw new RuntimeException("Something wrong happen.");
        }
    }

    /**
     * @param app which is the application to apply the method
     */
    public static void fillLatencyPerProviders(
        Application app, Map<Integer, Map<Integer, Double>> latency
    ) {
        // Get root
        Node root = app.getGraph();
        // Build matrix with a recursive method with the parent and children
        _getLatencyPerProviders(app, root, root.getNext(), new HashSet<>(), latency);
    }

    private static void _getLatencyPerProviders(
        Application app, Node current, List<Node> next, Set<Integer> visited, Map<Integer, Map<Integer, Double>> latency
    ) {
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

                    // Create a dictionary for this genotype
                    latency.put(iGenotype, new HashMap<>());
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
                    latency.get(iGenotype).put(p, lat);
                }

                next.forEach(child -> _getLatencyPerProviders(app, child, child.getNext(), visited, latency));
            } else {
                current.getNext().forEach(p -> _getLatencyPerProviders(app, p, p.getNext(), visited, latency));
            }
        }
    }
}
