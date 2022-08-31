package executions;

import generators.*;
import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.Range;
import models.auxiliary.TimeLimit;
import models.enums.*;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import resolvers.DAC;
import resolvers.GA;
import utils.CSV;
import utils.Data;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Paper {

    public static void main(String[] args) throws Exception {
//        scaling(-1L, 10);
//        batchSize(-1L, 10);
//        variablesProviders(-1L, 10);
//        slopeIntercept(-1L, 5);

        resolvingParallels(-1L, 10);
//        resolvingParallelsVariable(-1L, 10);
//        resolvingParallels(20_284L, 1);
//        resolvingParallels(20_581L, 1);
    }

    public static void scaling(Long seed, Integer iterations) throws IOException {
        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("01_scaling_sequential", new HashMap<>() {{
                put(ArchitecturePattern.SEQUENTIAL, 1.);
            }});
        }};

        // Hyperparameters tuning
        Map<CONFIG, List<Object>> hyperparameters = new HashMap<>() {{
            put(CONFIG.PROVIDERS, List.of(3, 5, 7, 10, 15));
            put(CONFIG.SERVICES, List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000));
        }};

        TimeLimit timeLimit = new TimeLimit(Duration.ofSeconds(12));

        // Attributes to measure
        List<QoS> qoSList = List.of(QoS.COST, QoS.RESPONSE_TIME);

        // Define headers to get information
        List<Header> headers = Headers.all();

        // Result variables
        Quartet<Integer, Integer, Integer, Integer> patternsCounted;

        // Each iteration of this loop, we added more architectures patterns
        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {

            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            // Iterations of services
            for (Object nServices : hyperparameters.get(CONFIG.SERVICES)) {
                for (Object nProviders : hyperparameters.get(CONFIG.PROVIDERS)) {

                    int nOfServices = (int) nServices, nOfProviders = (int) nProviders;

                    System.out.printf("Processing %d services with %d providers...%n", nOfServices, nOfProviders);

                    // Times to repeat problem
                    for (int i = 1; i <= iterations; i++) {

                        System.out.printf("  Iteration: %d/%d%n", i, iterations);

                        // Prepare an instance of full problem
                        Application problem = Applications.get(
                            nOfProviders, nOfServices, qoSList, seed, configuration.getValue()
                        );

                        // Get best and worst estimate values
                        Pair<Pair<Integer, Double>, Pair<Integer, Double>> BaW = problem.estimateBestAndWorstProviders();

                        // Count the different patterns
                        patternsCounted = problem.getArchitecture().countPatterns();

                        // Calculate full problem
                        GA.resolveByGA(problem, data);

                        updateData(patternsCounted, data, nServices, BaW, timeLimit);

                        // Saving data
                        CSV.save(headers, data, configuration.getKey());
                    }
                }
            }
        }
    }

    public static void batchSize(Long seed, Integer iterations) throws IOException {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("02_batch_size_no_parallels", getNoParallelsProbabilities());
            put("02_batch_size_all", ArchitectureProbabilities.get());
        }};

        // Hyperparameters tuning
        Map<CONFIG, List<Object>> hyperparameters = new HashMap<>() {{
            put(CONFIG.PROVIDERS, Collections.singletonList(10));
            put(CONFIG.SERVICES, List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000));
            put(CONFIG.BATCH_SIZE, List.of(0, 1, 5, 10, 20, 30, 50));
        }};

        // Attributes for measure
        List<QoS> qosList = List.of(QoS.COST, QoS.RESPONSE_TIME);

        // Define headers to get information
        List<Header> headers = Headers.all();

        // Define time limit
        TimeLimit timeLimit = new TimeLimit(Duration.ofSeconds(400));

        // Define providers
        Range<Integer> providersRange = new Range<>(5);

        // Each iteration of this loop, we added more architectures patterns
        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {

            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            // Iterations of services
            for (Object nServices : hyperparameters.get(CONFIG.SERVICES)) {
                for (Object nProviders : hyperparameters.get(CONFIG.PROVIDERS)) {

                    int nOfServices = (int) nServices, nOfProviders = (int) nProviders;

                    System.out.printf("Processing %d services with %d providers...%n", nOfServices, nOfProviders);

                    // Times to repeat problem
                    for (int i = 1; i <= iterations; i++) {

                        System.out.printf("  Iteration: %d/%d%n", i, iterations);

                        // Prepare an instance of full problem
                        Application problem = Applications.get(
                            nOfProviders, nOfServices, qosList, seed, configuration.getValue()
                        );

                        runProblem(
                            problem, timeLimit, hyperparameters, configuration, data, providersRange, false,
                            threadId
                        );
                    }
                }
            }
        }
    }

    public static void variablesProviders(Long seed, Integer iterations) throws IOException {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("03_services_with_variables_providers_no_parallels", getNoParallelsProbabilities());
            put("03_services_with_variables_providers", ArchitectureProbabilities.get());
        }};

        // Hyperparameters tuning
        Map<CONFIG, List<Object>> hyperparameters = new HashMap<>() {{
            put(CONFIG.PROVIDERS, Collections.singletonList(15));
            put(CONFIG.SERVICES, List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000));
            put(CONFIG.BATCH_SIZE, List.of(0, 1, 5, 10));
        }};

        TimeLimit timeLimit = new TimeLimit(Duration.ofSeconds(400));

        // Attributes for measure
        List<QoS> qoSList = List.of(QoS.COST, QoS.RESPONSE_TIME);

        // Define providers
        Range<Integer> providersRange = new Range<>(5);

        // Define headers to get information
        List<Header> headers = Headers.all();

        // Each iteration of this loop, we added more architectures patterns
        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {

            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            // Iterations of services
            for (Object nServices : hyperparameters.get(CONFIG.SERVICES)) {
                for (Object nProviders : hyperparameters.get(CONFIG.PROVIDERS)) {

                    int nOfServices = (int) nServices, nOfProviders = (int) nProviders;

                    System.out.printf("Processing %d services with %d providers...%n", nOfServices, nOfProviders);

                    // Times to repeat problem
                    for (int i = 1; i <= iterations; i++) {

                        System.out.printf("  Iteration: %d/%d%n", i, iterations);

                        // Prepare an instance of full problem
                        Application problem = Applications.get(
                            nOfProviders, nOfServices, qoSList, seed, configuration.getValue()
                        );

                        runProblem(
                            problem, timeLimit, hyperparameters, configuration, data, providersRange, false,
                            threadId
                        );
                    }
                }
            }
        }
    }

    public static void slopeIntercept(Long seed, Integer iterations) throws IOException {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("07_slope", ArchitectureProbabilities.get());
        }};

        int batchSize0 = 0, batchSize1 = 1, nOfProviders = 10;

        List<Object> batchSizes = new ArrayList<>();
        batchSizes.add(batchSize0);
        batchSizes.add(batchSize1);

        // Hyperparameters tuning
        Map<CONFIG, List<Object>> hyperparameters = new HashMap<>() {{
            put(CONFIG.SERVICES, List.of(600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000));
            put(CONFIG.BATCH_SIZE, batchSizes);
        }};

        // Attributes for measure
        List<QoS> qosList = List.of(QoS.COST, QoS.RESPONSE_TIME);

        // Define headers to get information
        List<Header> headers = Headers.all();

        // Define slopes
        List<Long> slopes = List.of(50L, 40L, 30L, 20L, 10L);

        // Define time limit
        TimeLimit timeLimit = new TimeLimit(-1L, 30L);

        // Define providers
        Range<Integer> providersRange = new Range<>(5);

        // Each iteration of this loop, we added more architectures patterns
        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {

            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            for (Object nServices : hyperparameters.get(CONFIG.SERVICES)) {

                int nOfServices = (int) nServices;
                System.out.printf("Processing %d services...%n", nOfServices);

                // Times to repeat problem
                for (int i = 1; i <= iterations; i++) {
                    System.out.printf("  Iteration: %d/%d%n", i, iterations);

                    // Prepare an instance of full problem
                    Application problem = Applications.get(
                        nOfProviders, nOfServices, qosList, seed, configuration.getValue()
                    );

                    // Iterations of services
                    for (Long slope : slopes) {

                        // Set slope time for current execution
                        timeLimit.setSlope(slope);

                        System.out.printf("Slope %d...%n", slope);

                        // Run problem
                        runProblem(
                            problem, timeLimit, hyperparameters, configuration, data, providersRange, false,
                            threadId
                        );
                    }
                }
            }
        }
    }

    public static void resolvingParallels(Long seed, Integer iterations) throws IOException {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("08_resolving_parallels", ArchitectureProbabilities.get());
        }};

        // Hyperparameters tuning
        Map<CONFIG, List<Object>> hyperparameters = new HashMap<>() {{
            put(CONFIG.SERVICES, List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000));
            put(CONFIG.BATCH_SIZE, List.of(0, 1, 2, 3, 4, 5, 6));
        }};

        // Attributes for measure
        List<QoS> qos = List.of(QoS.COST, QoS.RESPONSE_TIME);

        // Define headers to get information
        List<Header> headers = Headers.all();

        // Define execution variables
        int nOfProviders = 10;

        // Define time limit
        TimeLimit timeLimit = new TimeLimit(40L, 30L);

        // Define providers
        Range<Integer> providersRange = new Range<>(5);

        // Each iteration of this loop, we added more architectures patterns
        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {

            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            for (Object nServices : hyperparameters.get(CONFIG.SERVICES)) {

                int nOfServices = (int) nServices;
                System.out.printf("Processing %d services...%n", nOfServices);

                // Times to repeat problem
                for (int i = 1; i <= iterations; i++) {
                    System.out.printf("  Iteration: %d/%d%n", i, iterations);

                    // Define providers and services
                    List<Provider> providers = Providers.get(nOfProviders, qos, seed);
                    List<Service> services = Services.get(nOfServices, providers, providersRange, seed);

                    // Prepare an instance of full problem
                    Application problem = Applications.get(
                        providers, services, qos, seed, configuration.getValue(), providersRange
                    );

                    // Run problem
                    runProblem(
                        problem, timeLimit, hyperparameters, configuration, data, providersRange, true,
                        threadId
                    );
                }
            }
        }
    }

    public static void resolvingParallelsVariable(Long seed, Integer iterations) throws IOException {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("08_resolving_parallels_variable", ArchitectureProbabilities.get());
        }};

        // Hyperparameters tuning
        Map<CONFIG, List<Object>> hyperparameters = new HashMap<>() {{
            put(CONFIG.SERVICES, List.of(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000));
            put(CONFIG.BATCH_SIZE, List.of(0, 1, 2, 3, 4, 5, 6));
        }};

        // Attributes for measure
        List<QoS> qosList = List.of(QoS.COST, QoS.RESPONSE_TIME);

        // Define headers to get information
        List<Header> headers = Headers.all();

        // Define execution variables
        int nOfProviders = 15;

        // Define time limit
        TimeLimit timeLimit = new TimeLimit(40L, 30L);

        Range<Integer> providersRange = new Range<>(2, 7);

        // Each iteration of this loop, we added more architectures patterns
        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {

            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            for (Object nServices : hyperparameters.get(CONFIG.SERVICES)) {

                int nOfServices = (int) nServices;
                System.out.printf("Processing %d services...%n", nOfServices);

                // Times to repeat problem
                for (int i = 1; i <= iterations; i++) {
                    System.out.printf("  Iteration: %d/%d%n", i, iterations);

                    // Prepare an instance of full problem
                    Application problem = Applications.get(
                        nOfProviders, nOfServices, qosList, seed, NormalizedMethod.MAX, configuration.getValue(),
                        providersRange
                    );

                    // Run problem
                    runProblem(
                        problem, timeLimit, hyperparameters, configuration, data, providersRange, true,
                        threadId
                    );
                }
            }
        }
    }

    private static void runProblem(
        Application problem, TimeLimit timeLimit, Map<CONFIG, List<Object>> hyperparameters,
        Map.Entry<String, Map<ArchitecturePattern, Double>> configuration, Map<Header, List<Object>> data,
        Range<Integer> providersRange, Boolean splitParallels, Long threadId
    ) throws IOException {

        // Count the different patterns
        Quartet<Integer, Integer, Integer, Integer> patternsCounted = problem.getArchitecture().countPatterns();
        Pair<Pair<Integer, Double>, Pair<Integer, Double>> BaW;
        int nServices = problem.getServices().size();
        Duration limitTime;

        for (Object nBatchSize : hyperparameters.get(CONFIG.BATCH_SIZE)) {
            // Transform batch size
            int batchSize = (int) nBatchSize;

            // Provisional change
            switch (batchSize) {
                case 0 -> {
                    // Full resolver with slope 40ms and intercept 30ms
                    timeLimit = new TimeLimit(40L, 30L);
                    batchSize = 0;
                }
                case 1 -> {
                    // Batch size 1 without splitting parallels, slope 40ms and intercept 30ms
                    timeLimit = new TimeLimit(40L, 30L);
                    batchSize = 1;
                    splitParallels = false;
                }
                case 2 -> {
                    // Batch size 1 with splitting parallels, slope 40ms and intercept 30ms
                    timeLimit = new TimeLimit(40L, 30L);
                    batchSize = 1;
                    splitParallels = true;
                }
                case 3 -> {
                    // Full without time limit
                    timeLimit = new TimeLimit(Duration.ofDays(1));
                    batchSize = 0;
                }
                case 4 -> {
                    // Batch size 1 with stop in parallels without time limit
                    // Full without time limit
                    timeLimit = new TimeLimit(Duration.ofDays(1));
                    batchSize = 1;
                    splitParallels = false;
                }
                case 5 -> {
                    // Full resolver with slope 30ms and intercept 30ms
                    timeLimit = new TimeLimit(30L, 30L);
                    batchSize = 0;
                }
                case 6 -> {
                    // Batch size 1 without splitting parallels, slope 30ms and intercept 30ms
                    timeLimit = new TimeLimit(30L, 30L);
                    batchSize = 1;
                    splitParallels = false;
                }
            }

            BaW = new Pair<>(new Pair<>(1, 1.), new Pair<>(1, 0.));

//            // Get best and worst estimate values
//            if (providersRange.hasRange()) {
//                BaW = new Pair<>(new Pair<>(1, 1.), new Pair<>(1, 0.));
//            } else {
//                BaW = problem.estimateBestAndWorstProviders();
//            }

            if (batchSize < 1) {
                limitTime = timeLimit.isAdaptive() ? timeLimit.getApproximateLimitTime(nServices) : timeLimit.getDuration();
                GA.resolveByGA(problem, data);
            } else {
                limitTime = timeLimit.isAdaptive() ? timeLimit.getApproximateLimitTime(batchSize) : timeLimit.getDuration();
                DAC.resolve(problem, batchSize, data, timeLimit, splitParallels, threadId);
            }

            System.out.printf("  - Batch of %s done%n", batchSize);

            // Update data into map
            updateData(patternsCounted, data, nServices, BaW, timeLimit);

            // Add time limit
            data.get(Header.LIMIT_TIME).add(limitTime.toMillis());
            data.get(Header.SPLIT_PARALLELS).add(splitParallels ? 1 : 0);
            data.get(Header.VARIABLE_PROVIDERS).add(providersRange.hasRange() ? 1 : 0);

            // Saving data
            CSV.save(Headers.all(), data, configuration.getKey());
        }
    }

    private static Map<ArchitecturePattern, Double> getNoParallelsProbabilities() {
        double prob = 1. / 3;
        return new HashMap<>() {{
            put(ArchitecturePattern.SEQUENTIAL, prob);
            put(ArchitecturePattern.CONDITIONAL, prob);
            put(ArchitecturePattern.ITERATIVE, prob);
        }};
    }

    private static void updateData(
        Quartet<Integer, Integer, Integer, Integer> patternsCounted, Map<Header, List<Object>> data, Object nServices,
        Pair<Pair<Integer, Double>, Pair<Integer, Double>> baW, TimeLimit timeLimit
    ) {
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.SERVICES, nServices),
            new AbstractMap.SimpleEntry<>(Header.ITERATIVE_PATTERNS, patternsCounted.getValue0()),
            new AbstractMap.SimpleEntry<>(Header.CONDITIONALS_PATTERNS, patternsCounted.getValue1()),
            new AbstractMap.SimpleEntry<>(Header.PARALLELS_PATTERNS, patternsCounted.getValue2()),
            new AbstractMap.SimpleEntry<>(Header.SEQUENTIAL_PATTERNS, patternsCounted.getValue3()),
            new AbstractMap.SimpleEntry<>(Header.BEST_PROVIDER, baW.getValue0().getValue0()),
            new AbstractMap.SimpleEntry<>(Header.BEST_PROVIDER_VALUE, baW.getValue0().getValue1()),
            new AbstractMap.SimpleEntry<>(Header.WORST_PROVIDER, baW.getValue1().getValue0()),
            new AbstractMap.SimpleEntry<>(Header.WORST_PROVIDER_VALUE, baW.getValue1().getValue1()),
            new AbstractMap.SimpleEntry<>(Header.SLOPE, timeLimit.getSlope()),
            new AbstractMap.SimpleEntry<>(Header.INTERCEPT, timeLimit.getIntercept())
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
