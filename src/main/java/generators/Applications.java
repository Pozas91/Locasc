package generators;

import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.Range;
import models.enums.ArchitecturePattern;
import models.enums.NormalizedMethod;
import models.enums.QoS;
import models.patterns.Architecture;
import models.patterns.Component;
import models.patterns.IndexService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Applications {
    /**
     * Get a random application with the parameters specified
     *
     * @param providers       List of providers for this application
     * @param services        List of services for this application
     * @param qos             A list of attributes to take into account
     * @param seed            A specific seed to generate random instance
     * @param method          Method used to normalize values
     * @param probOfPatterns  A map with the probability of each pattern appears into generated application
     * @param rangeComponents Range number of components per architecture
     * @return An application
     */
    public static Application get(
        List<Provider> providers, List<Service> services, List<QoS> qos, Long seed, NormalizedMethod method,
        Map<ArchitecturePattern, Double> probOfPatterns, Range<Integer> rangeComponents, Range<Integer> rangeProviders
    ) {
        List<Component> components = getComponents(services.size());
        Architecture architecture = Architectures.get(components, probOfPatterns, rangeComponents, seed);
        Application app = new Application(architecture, services, providers, Weights.get(qos), method, qos);

        if (qos.contains(QoS.LATENCY) || qos.contains(QoS.THROUGHPUT)) {
            // Set providers for gates
            Gates.setProviders(
                app.getGates(), providers, rangeProviders == null ? new Range<>(providers.size()) : rangeProviders, seed
            );

            app.updateGatesToExplore();
        }

        app.updateProvidersNormalization();
        app.updateAppNormalization();

        return app;
    }

    public static Application get(
        List<Provider> providers, List<Service> services, List<QoS> qos, Long seed, NormalizedMethod nMethod,
        Map<ArchitecturePattern, Double> probOfPatterns, Range<Integer> componentsRange
    ) {
        return get(providers, services, qos, seed, nMethod, probOfPatterns, componentsRange, null);
    }

    public static Application get(
        List<Provider> providers, List<Service> services, List<QoS> qos, Long seed, NormalizedMethod nMethod,
        Map<ArchitecturePattern, Double> probOfPatterns
    ) {
        return get(providers, services, qos, seed, nMethod, probOfPatterns, new Range<>(2, 10));
    }

    public static Application get(
        List<Provider> providers, List<Service> services, List<QoS> qos, Long seed,
        Map<ArchitecturePattern, Double> probOfPatterns, Range<Integer> providersRange
    ) {
        return get(providers, services, qos, seed, NormalizedMethod.MAX, probOfPatterns, new Range<>(2, 7), providersRange);
    }

    public static Application
    get(List<Provider> providers, List<Service> services, List<QoS> qos, Long seed, NormalizedMethod nMethod) {
        return get(providers, services, qos, seed, nMethod, ArchitectureProbabilities.get());
    }

    public static Application get(List<Provider> providers, List<Service> services, List<QoS> qos, Long seed) {
        return get(providers, services, qos, seed, NormalizedMethod.MAX);
    }

    public static Application get(
        List<Provider> providers, List<Service> services, List<QoS> qos, Long seed,
        Map<ArchitecturePattern, Double> probOfPatterns
    ) {
        return get(providers, services, qos, seed, NormalizedMethod.MAX, probOfPatterns, null);
    }

    public static Application get(
        Integer nOfProviders, Integer nOfServices, List<QoS> qos, Long seed, NormalizedMethod nMethod,
        Map<ArchitecturePattern, Double> probOfPatterns, Range<Integer> providersRange
    ) {
        // Define providers and services
        List<Provider> providers = Providers.get(nOfProviders, qos, seed);
        List<Service> services;

        if (providersRange.hasRange()) {
            services = Services.get(nOfServices, providers, providersRange, seed);
        } else {
            services = Services.get(nOfServices, providers, providersRange);
        }

        return get(providers, services, qos, seed, nMethod, probOfPatterns);
    }

    public static Application get(
        Integer nOfProviders, Integer nOfServices, List<QoS> qos, Long seed, NormalizedMethod nMethod,
        Map<ArchitecturePattern, Double> probOfPatterns
    ) {
        return get(nOfProviders, nOfServices, qos, seed, nMethod, probOfPatterns, new Range<>(2, 7));
    }

    public static Application get(
        Integer nOfProviders, Integer nOfServices, List<QoS> qos, Long seed,
        Map<ArchitecturePattern, Double> probOfPatterns
    ) {
        return get(nOfProviders, nOfServices, qos, seed, NormalizedMethod.MAX, probOfPatterns);
    }

    private static List<Component> getComponents(Integer serviceSize) {
        return IntStream.range(0, serviceSize).mapToObj(IndexService::i).collect(Collectors.toList());
    }
}
