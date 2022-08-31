package generators;

import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Services {
    /**
     * Return a list of services which associate providers list is always the same
     *
     * @param nOfServices Number of services to generate
     * @param providers   List of providers to associate
     * @return A list of services
     */
    private static List<Service> get(Integer nOfServices, List<Provider> providers) {
        List<Integer> providersIndexes = IntStream
            .range(0, providers.size())
            .parallel()
            .boxed().collect(Collectors.toList());

        return IntStream.range(0, nOfServices)
            .mapToObj(i -> new Service("W" + i, new ArrayList<>(providersIndexes)))
            .collect(Collectors.toList());
    }

    /**
     * Return a list of services which associate providers, each service will has a list [from, to] providers randomly.
     *
     * @param nOfServices Number of services to generate
     * @param providers   List of providers to associate
     * @param range       Range of providers per service
     * @return A list of services
     */
    public static List<Service> get(Integer nOfServices, List<Provider> providers, Range<Integer> range) {
        return get(nOfServices, providers, range, -1L);
    }

    /**
     * Return a list of services which associate providers, each service will has a list [from, to] providers with a
     * specific seed.
     *
     * @param nOfServices Number of services to generate
     * @param providers   List of providers to associate
     * @param range       Range of providers per service
     * @param seed        Specific seed to set random instance
     * @return A list of services
     */
    public static List<Service> get(Integer nOfServices, List<Provider> providers, Range<Integer> range, Long seed) {
        // If no range is provided return a fixed providers list per service
        if (!range.hasRange()) {
            return get(nOfServices, providers);
        } else {
            // Prepare random
            Random rnd = (seed >= 0) ? new Random(seed) : new Random();

            // Discard first random number
            rnd.nextDouble();

            int pSize = providers.size(), to = range.to(), from = range.from();
            List<Integer> listOfProviders = IntStream.range(0, pSize).boxed().collect(Collectors.toList());

            if (to > pSize) {
                throw new RuntimeException(
                    "You have chosen a range of providers greater than those available, please check."
                );
            }

            return IntStream.range(0, nOfServices).mapToObj(i -> {
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

                return new Service("W" + i, pService);
            }).collect(Collectors.toList());
        }
    }
}
