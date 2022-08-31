package generators;

import models.applications.Gate;
import models.applications.Provider;
import models.auxiliary.Node;
import models.auxiliary.Range;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Gates {
    public static List<Gate> extract(Node root) {
        // Walking through graph to extract gates
        Set<Integer> ids = root.getNext().parallelStream()
            .map(Gates::walking)
            .flatMap(Collection::parallelStream)
            .collect(Collectors.toSet());

        return ids.parallelStream().sorted().map(i -> {
            Gate g = new Gate(i);
            g.setApp(root.getApp());
            return g;
        }).collect(Collectors.toList());
    }

    private static Set<Integer> walking(Node root) {
        Set<Integer> ids = new HashSet<>();

        // Get gate identifier
        int gateID = root.getGateID();

        if (gateID >= 0) {
            ids.add(gateID);
        }

        Set<Integer> subIds = root.getNext().parallelStream()
            .map(Gates::walking)
            .flatMap(Collection::parallelStream)
            .collect(Collectors.toSet());

        ids.addAll(subIds);

        return ids;
    }

    public static void setProviders(List<Gate> gates, List<Provider> providers, Range<Integer> range, Long seed) {
        // If no range is provided return a fixed providers list per service
        if (!range.hasRange()) {
            List<Integer> providersIndexes = IntStream
                .range(0, providers.size())
                .parallel().boxed()
                .collect(Collectors.toList());

            for (Gate g : gates) {
                g.addCandidates(providersIndexes);
            }
        } else {
            // Prepare random
            Random rnd = (seed >= 0) ? new Random(seed) : new Random();

            // Discard first random number
            rnd.nextDouble();

            // Get providers size to be populated
            int pSize = providers.size(), to = range.to(), from = range.from();
            List<Integer> listOfProviders = IntStream.range(0, pSize).boxed().collect(Collectors.toList());

            if (to > pSize) {
                throw new RuntimeException(
                    "You have chosen a range of providers greater than those available, please check."
                );
            }

            for (Gate g : gates) {
                /*
                 * With the example ['P1', 'P2', 'P3', 'P4', ..., 'P10'] (length 10)
                 * - range statement will return a number between [from, to] (`to` limits is 10)
                 * - map statement will return an index between [0, 9]
                 */
                // For each iteration get random provider from the list, [2, 3, 0, 8, 1, 10]
                int nOfProviders = rnd.nextInt(to - from) + from;

                // Shuffling providers list
                Collections.shuffle(listOfProviders, rnd);

                // Providers selected for current service
                List<Integer> pService = new ArrayList<>();

                for (int k = 0; k < nOfProviders; k++) {
                    pService.add(listOfProviders.get(k));
                }

                g.addCandidates(pService);
            }
        }
    }
}