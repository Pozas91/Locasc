package generators;

import com.github.javafaker.Faker;
import models.applications.Provider;
import models.enums.ConnRange;
import models.enums.QoS;
import models.geo.Location;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple class to generate a list of providers with some specific configuration
 */
public class Providers {
    /**
     * Return a list of 'nOfProviders' providers controlled with random instance
     *
     * @param nOfProviders Number of random providers to get
     * @param seed         Seed to random execution
     * @param qos          List of QoS attributes to generate providers
     * @return List of providers
     */
    public static List<Provider> get(Integer nOfProviders, List<QoS> qos, Long seed, Boolean sameProvider) {
        // Prepare random
        Random rnd = (seed >= 0) ? new Random(seed) : new Random();

        // Discard first random number
        rnd.nextDouble();

        Faker faker = new Faker(rnd);
        List<Provider> providers = new ArrayList<>();
        Map<QoS, Double> attributes = getAttributes(qos, rnd);

        // Adding connections information
        int connRanges = ConnRange.values().length;

        for (int i = 0; i < nOfProviders; i++) {
            String name = faker.superhero().name();

            if (!sameProvider) {
                attributes = getAttributes(qos, rnd);
            }

            // Get random location
            Location location = Locations.getRandom(rnd);

            // Get connection range
            ConnRange connRange = ConnRange.getRange(rnd.nextInt(connRanges));

            // Create provider
            Provider provider = new Provider(name, attributes, location, connRange);
            providers.add(provider);
        }

        return providers;
    }

    public static List<Provider> get(Integer nOfProviders, List<QoS> qos, Long seed) {
        return get(nOfProviders, qos, seed, false);
    }

    /**
     * Return a list of 'nOfProviders' providers with random attributes
     *
     * @param nOfProviders Number of random providers to get
     * @param qoSList      List of QoS attributes to generate providers
     * @return List of providers
     */
    public static List<Provider> get(Integer nOfProviders, List<QoS> qoSList) {
        return get(nOfProviders, qoSList, -1L);
    }


    /**
     * Generate random attributes:
     * - COST, RESPONSE TIME -> [1, 100]
     * - Default -> [0, 1]
     *
     * @param qos List of QoS attributes to generate attributes
     * @param rnd A random instance to generate values of these attributes
     * @return A map whose keys are QoS attributes and values are double values.
     */
    private static Map<QoS, Double> getAttributes(List<QoS> qos, Random rnd) {
        return qos.stream()
            .filter(q -> !q.equals(QoS.LATENCY) && !q.equals(QoS.THROUGHPUT))
            .map(q -> new AbstractMap.SimpleEntry<>(q, switch (q) {
                case COST, RESPONSE_TIME -> (rnd.nextDouble() * 99) + 1;
                default -> rnd.nextDouble();
            }))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }
}
