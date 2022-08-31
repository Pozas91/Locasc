package models.auxiliary;

import models.applications.Gate;
import models.applications.Provider;
import models.enums.ConnRange;
import models.patterns.IndexService;
import org.javatuples.Pair;
import utils.Sets;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Throughput {

    public static Pair<Double, Double> minMax(Node x) {
        Pair<Integer, Integer> minMaxLevels = minMax(x, new ConcurrentHashMap<>());

        return new Pair<>(
            ConnRange.getRange(minMaxLevels.getValue0()).getCapacity(),
            ConnRange.getRange(minMaxLevels.getValue1()).getCapacity()
        );
    }

    public static Double get(Node x, Long threadId) {
        return get(x, threadId, new ConcurrentHashMap<>());
    }

    public static Double get(Node x, List<Integer> composition) {
        return get(x, composition, new ConcurrentHashMap<>());
    }

    private static Pair<Integer, Integer> minMax(Node x, ConcurrentMap<Node, Pair<Integer, Integer>> cache) {
        if (x.getNext().isEmpty()) {
            return new Pair<>(Integer.MAX_VALUE, Integer.MIN_VALUE);
        } else {
            Stream<Pair<Integer, Integer>> stream = IntStream.range(0, x.getNext().size())
                .parallel().mapToObj(i -> {
                    // Get next node
                    Node n = x.getNext(i);
                    int dMin = Integer.MAX_VALUE, dMax = Integer.MIN_VALUE;

                    // Define f(n)
                    Pair<Integer, Integer> f_n;

                    // There are 3 types of nodes:
                    // 1. Initial and Last -> _location != null
                    // 2. IndexService -> _component != null
                    // 3. GateID -> _gateID >= 0

                    Set<ConnRange> xConnRanges = getConnections(x), nConnRanges = getConnections(n);

                    if (xConnRanges.isEmpty() && !nConnRanges.isEmpty()) {
                        for (ConnRange nConnRange : nConnRanges) {
                            dMin = Math.min(dMin, nConnRange.getLevel());
                            dMax = Math.max(dMax, nConnRange.getLevel());
                        }
                    } else if (!xConnRanges.isEmpty() && nConnRanges.isEmpty()) {
                        for (ConnRange xConnRange : xConnRanges) {
                            dMin = Math.min(dMin, xConnRange.getLevel());
                            dMax = Math.max(dMax, xConnRange.getLevel());
                        }
                    } else {
                        for (ConnRange a : xConnRanges) {
                            for (ConnRange b : nConnRanges) {
                                // Calculate intersection between levels
                                int level = Math.min(a.getLevel(), b.getLevel());

                                // Extract max and min levels
                                dMin = Math.min(dMin, level);
                                dMax = Math.max(dMax, level);
                            }
                        }
                    }

                    // Get f(n)
                    if (cache.containsKey(n)) {
                        f_n = cache.get(n);
                    } else {
                        f_n = minMax(n, cache);
                        cache.put(n, f_n);
                    }

                    return new Pair<>(Math.min(dMin, f_n.getValue0()), Math.max(dMax, f_n.getValue1()));
                });

            return stream.reduce(new Pair<>(Integer.MAX_VALUE, Integer.MIN_VALUE), (t1, t2) -> new Pair<>(
                Math.min(t1.getValue0(), t2.getValue0()), Math.max(t1.getValue1(), t2.getValue1())
            ));
        }
    }

    private static Set<ConnRange> getConnections(Node x) {
        if (x.getLocation() != null) {
            return Sets.of();
        } else if (x.getComponent() != null) {
            IndexService s = (IndexService) x.getComponent();
            return s.getConnRanges(x.getApp());
        } else if (x.getGateID() >= 0) {
            Gate g = x.getApp().getGate(x.getGateID());
            return g.getConnRanges();
        } else {
            throw new RuntimeException("Something wrong happen getting connections ranges in throughput function.");
        }
    }

    private static Double get(Node x, Long threadId, ConcurrentMap<Node, Double> cache) {
        if (x.getNext().isEmpty()) {
            return Double.MAX_VALUE;
        } else {
            DoubleStream s = IntStream.range(0, x.getNext().size())
                .parallel()
                .mapToDouble(i -> {
                    // Get next node
                    Node n = x.getNext(i);

                    // Define f(n)
                    double f_n, t;

                    // Extract providers
                    Provider p1 = x.getProvider(threadId), p2 = n.getProvider(threadId);

                    if (p1 == null && p2 != null) {
                        t = p2.getConnRange().getCapacity();
                    } else if (p1 != null && p2 == null) {
                        t = p1.getConnRange().getCapacity();
                    } else if (p1 != null) {
                        t = p1.getConnRange().getCapacity(p2.getConnRange());
                    } else {
                        throw new RuntimeException(
                            "At least one of each pair must always have a provider assigned to it."
                        );
                    }

                    // Get f(n)
                    if (cache.containsKey(n)) {
                        f_n = cache.get(n);
                    } else {
                        f_n = get(n, threadId, cache);
                        cache.put(n, f_n);
                    }

                    return Math.min(f_n, t);
                });

            return s.min().orElse(0.);
        }
    }

    private static Double get(Node x, List<Integer> composition, ConcurrentMap<Node, Double> cache) {
        if (x.getNext().isEmpty()) {
            return Double.MAX_VALUE;
        } else {
            DoubleStream s = IntStream.range(0, x.getNext().size())
                .parallel()
                .mapToDouble(i -> {
                    // Get next node
                    Node n = x.getNext(i);

                    // Define f(n)
                    double f_n, t;

                    // Extract providers
                    Provider p1 = x.getProvider(composition), p2 = n.getProvider(composition);

                    if (p1 == null && p2 != null) {
                        t = p2.getConnRange().getCapacity();
                    } else if (p1 != null && p2 == null) {
                        t = p1.getConnRange().getCapacity();
                    } else if (p1 != null) {
                        t = p1.getConnRange().getCapacity(p2.getConnRange());
                    } else {
                        throw new RuntimeException(
                            "At least one of each pair must always have a provider assigned to it."
                        );
                    }

                    // Get f(n)
                    if (cache.containsKey(n)) {
                        f_n = cache.get(n);
                    } else {
                        f_n = get(n, composition, cache);
                        cache.put(n, f_n);
                    }

                    return Math.min(f_n, t);
                });

            return s.min().orElse(0.);
        }
    }
}
