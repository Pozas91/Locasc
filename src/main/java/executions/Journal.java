package executions;

import generators.Applications;
import generators.ArchitectureProbabilities;
import generators.Providers;
import generators.Services;
import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.Constraint;
import models.auxiliary.Range;
import models.auxiliary.TimeLimit;
import models.enums.*;
import resolvers.*;
import utils.CSV;
import utils.Data;
import utils.RunConf;
import utils.ToDebug;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

enum Resolver {
    GA, U, UM;
}

enum ConstraintExecution {
    NO, NORMAL, INVERTED;
}

public class Journal {
    // Define date format
    static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public static void main(String[] args) throws IOException {
        // Elbow method to choose the best fitness degree
        RunConf.instance().get().clear();
        elbow(-1L, 5, false);

        // Fitness evolutionGA
        RunConf.instance().get().clear();
        evolution(-1L, 5, Resolver.U);
        RunConf.instance().get().clear();
        evolution(-1L, 5, Resolver.GA);
        RunConf.instance().get().clear();
        evolution(-1L, 5, Resolver.UM);

        // Scalability experiment
        RunConf.instance().get().clear();
        scalability(-1L, 10);

        // Comparison methods
        RunConf.instance().get().clear();
        comparisonProviders(-1L, 10);
        RunConf.instance().get().clear();
        comparisonServices(-1L, 10, ConstraintExecution.NO);
        RunConf.instance().get().clear();
        comparisonServices(-1L, 10, ConstraintExecution.NORMAL);
        RunConf.instance().get().clear();
        comparisonServices(-1L, 10, ConstraintExecution.INVERTED);
    }

    private static void scalability(Long seed, Integer iterations) throws IOException {
        // Get architectures
        Map<ArchitecturePattern, Double> archProb = ArchitectureProbabilities.get(
            List.of(ArchitecturePattern.values())
        );

        // Generic variables
        List<QoS> qos = List.of(QoS.values());

        // Define limits
        int maxServices = 2_000, stepServices = 100, initServices = 100;

        // Get headers
        List<Header> headers = List.of(
            Header.RESOLVER, Header.LIMIT_TIME, Header.EXECUTION_TIME, Header.PROVIDERS, Header.SERVICES,
            Header.BEST_FITNESS, Header.MEAN_FITNESS, Header.PRE_CALCULATION_TIME
        );

        // Data to save in CSV
        Map<Header, List<Object>> data = Data.getDataMap(headers);

        // Define time limit
        TimeLimit tLimit = new TimeLimit(Duration.ofSeconds(60 * 3));
        RunConf.instance().set(CONFIG.TIME_LIMIT, tLimit);

        // Define list of providers
        List<Integer> listNOfProviders = List.of(100, 1_000);

        for (int nOfServices = initServices; nOfServices <= maxServices; nOfServices += stepServices) {
            for (int nOfProviders : listNOfProviders) {
                // Define providers range
                Range<Integer> providersRange = new Range<>(nOfProviders);

                for (int iter = 1; iter <= iterations; iter++) {
                    // For each iteration
                    System.out.printf("%nTime: %s%n", DATE_FORMAT.format(new Date()));
                    System.out.printf("# services: %d%n", nOfServices);
                    System.out.printf("# providers: %d%n", nOfProviders);
                    System.out.printf("Iteration: %d/%d%n%n", iter, iterations);

                    // Get providers and services
                    List<Provider> providers = Providers.get(nOfProviders, qos, seed);
                    List<Service> services = Services.get(nOfServices, providers, providersRange, seed);

                    // Prepare an instance of application
                    Application app = Applications.get(providers, services, qos, seed, archProb, providersRange);

                    // Resolve by express method
                    resolveExpress(data, tLimit, app);

                    // Resolve by utility method
                    RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
                    RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                    resolveU(data, tLimit, app, 100);

                    // Resolve by modified utility method
                    RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
                    RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                    resolveUM(data, tLimit, app, 70);

                    // Resolve by complete GA method
                    RunConf.instance().set(CONFIG.MUTATION_PROB, .01);
                    RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                    resolveGA(data, tLimit, app);

                    // Save information in CSV step by step.
                    CSV.save(headers, data, "scaling_u_um");
                }
            }
        }

        // Show executions ends!
        System.out.println("Done!");
    }

    private static void evolution(Long seed, Integer iterations, Resolver resolver) throws IOException {
        // Get architectures
        Map<ArchitecturePattern, Double> archProb = ArchitectureProbabilities.get(List.of(
            ArchitecturePattern.values()
        ));

        // Generic variables
        List<QoS> qos = List.of(QoS.values());

        // Get headers
        List<Header> headers = List.of(
            Header.RESOLVER, Header.LIMIT_TIME, Header.EXECUTION_TIME, Header.SERVICES, Header.PROVIDERS,
            Header.FITNESS, Header.MUTATION_PROB, Header.CONVERGENCE, Header.TIME
        );

        List<Map<CONFIG, Number>> configuration = new ArrayList<>();
        List<Double> listMutation, listConvergence;

        // U -> 0.02 - 0.03, 0.001
        // UM -> 0.01 - 0.02, 0.001
        // GA -> 0.01 - 0.02, 0.001

        listMutation = List.of(.005, .01, .015, .02, .025, .03, .035, .04, .045, .05, .06, .07, .08, .09, .10);
        listConvergence = List.of(1., .1, .01, .001, .0001);

        for (double mutationProb : listMutation) {
            for (double convergence : listConvergence) {
                Map<CONFIG, Number> conf = new HashMap<>();
                conf.put(CONFIG.MUTATION_PROB, mutationProb);
                conf.put(CONFIG.CONVERGENCE, convergence);
                configuration.add(conf);
            }
        }

        // Define time limit
        TimeLimit tLimit = new TimeLimit(Duration.ofSeconds(60));
        RunConf.instance().set(CONFIG.TIME_LIMIT, tLimit);
        RunConf.instance().set(CONFIG.EVOLUTION, true);

        // Services and providers
        int nOfServices = 500, nOfProviders = 500;

        for (int i = 0; i < iterations; i++) {
            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(headers);

            // Define providers range
            Range<Integer> providersRange = new Range<>(nOfProviders);

            // Get providers and services
            List<Provider> providers = Providers.get(nOfProviders, qos, seed);
            List<Service> services = Services.get(nOfServices, providers, providersRange, seed);

            // Prepare an instance of application
            Application app = Applications.get(providers, services, qos, seed, archProb, providersRange);

            for (Map<CONFIG, Number> conf : configuration) {
                // Set configuration
                double
                    convergence = (double) conf.get(CONFIG.CONVERGENCE),
                    mutationProb = (double) conf.get(CONFIG.MUTATION_PROB);
                RunConf.instance().set(CONFIG.CONVERGENCE, convergence);
                RunConf.instance().set(CONFIG.MUTATION_PROB, mutationProb);

                // For each configuration
                System.out.printf("%nTime: %s%n", DATE_FORMAT.format(new Date()));
                System.out.printf("Resolver: %s%n", resolver);
                System.out.printf("Convergence: %.4f%n", convergence);
                System.out.printf("Mutation prob: %.2f%n", mutationProb);
                System.out.printf("Iteration: %d%n%n", i);

                // Launch resolver
                switch (resolver) {
                    case U -> resolveU(data, tLimit, app, 100);
                    case UM -> resolveUM(data, tLimit, app, 70);
                    case GA -> resolveGA(data, tLimit, app);
                }

                data.get(Header.FITNESS).add(new ArrayList<>(ToDebug.getInstance().getFitness()));
                data.get(Header.TIME).add(new ArrayList<>(ToDebug.getInstance().getTime()));
                data.get(Header.MUTATION_PROB).add(mutationProb);
                data.get(Header.CONVERGENCE).add(convergence);

                // Clear for next iteration
                ToDebug.getInstance().clear();

                // Save information in CSV step by step.
                CSV.save(headers, data, String.format("evolution_%s_%d", resolver, i));
            }
        }

        // Show executions ends!
        System.out.println("Done!");
    }

    private static void comparisonServices(
        Long seed, Integer iterations, ConstraintExecution cExecution
    ) throws IOException {
        // Get architectures
        Map<ArchitecturePattern, Double> archProb = ArchitectureProbabilities.get(List.of(
            ArchitecturePattern.values()
        ));

        // Generic variables
        List<QoS> qos = List.of(QoS.values());

        // Define limits
        int nOfProviders = 500, maxServices = 1_000, stepServices = 100, initServices = 100;

        // Get headers
        List<Header> headers = List.of(
            Header.RESOLVER, Header.LIMIT_TIME, Header.EXECUTION_TIME, Header.PROVIDERS, Header.SERVICES,
            Header.BEST_FITNESS, Header.MEAN_FITNESS, Header.PRE_CALCULATION_TIME, Header.GENERATIONS
        );

        // Data to save in CSV
        Map<Header, List<Object>> data = Data.getDataMap(headers);

        // Define time limit
        TimeLimit tLimit = new TimeLimit(Duration.ofSeconds(60 * 3));
        RunConf.instance().set(CONFIG.TIME_LIMIT, tLimit);

        // Define providers range
        Range<Integer> providersRange = new Range<>(nOfProviders);

        // Name file
        String fileName = switch (cExecution) {
            case NO -> "comparison_services";
            case NORMAL -> "comparison_services_constraints";
            case INVERTED -> "comparison_services_constraints_inverted";
        };

        for (int nOfServices = initServices; nOfServices <= maxServices; nOfServices += stepServices) {
            // For each iteration
            for (int iter = 1; iter <= iterations; iter++) {
                System.out.printf("%nTime: %s%n", DATE_FORMAT.format(new Date()));
                System.out.printf("# services: %d%n", nOfServices);
                System.out.printf("# providers: %d%n", nOfProviders);
                System.out.printf("Iteration: %d/%d%n%n", iter, iterations);

                // Get providers and services
                List<Provider> providers = Providers.get(nOfProviders, qos, seed);
                List<Service> services = Services.get(nOfServices, providers, providersRange, seed);

                // Prepare an instance of application
                Application app = Applications.get(providers, services, qos, seed, archProb, providersRange);

                switch (cExecution) {
                    case NORMAL -> {
                        for (QoS k : qos) {

                            // Skip latency to avoid fails
                            if (k.equals(QoS.LATENCY)) {
                                continue;
                            }

                            ConstraintOperator operator;
                            double bound;

                            if (k.getObjective().equals(ObjectiveFunction.MINIMIZE)) {
                                // Minimize attribute must be less than 130% of the minimum value
                                operator = ConstraintOperator.LESS_THAN;
                                bound = app.getAppNorm().get(k).getMin() * 1.3;
                            } else {
                                // Maximize attribute must be greater than 70% of the maximum value
                                operator = ConstraintOperator.GREATER_THAN;
                                bound = app.getAppNorm().get(k).getMax() * .7;
                            }

                            app.putSoftConstraint(k, new Constraint(operator, bound));
                        }
                    }
                    case INVERTED -> {
                        for (QoS k : qos) {

                            // Skip latency to avoid fails
                            if (k.equals(QoS.LATENCY)) {
                                continue;
                            }

                            ConstraintOperator operator;
                            double bound;

                            if (k.getObjective().equals(ObjectiveFunction.MINIMIZE)) {
                                // Minimize attribute must be greater than 130% of the minimum value (Bad value)
                                operator = ConstraintOperator.GREATER_THAN;
                                bound = app.getAppNorm().get(k).getMax() * 1.3;
                            } else {
                                // Maximize attribute must be less than 70% of the maximum value (Bad value)
                                operator = ConstraintOperator.LESS_THAN;
                                bound = app.getAppNorm().get(k).getMax() * .7;
                            }

                            app.putSoftConstraint(k, new Constraint(operator, bound));
                        }
                    }
                }

                // Set a weight for constraints
                if (!cExecution.equals(ConstraintExecution.NO)) {
                    app.setSoftConstraintsW(.8);
                }

                // Resolve by random method
                resolveRND(seed, data, tLimit, app);

                // Resolve by express method
                resolveExpress(data, tLimit, app);

                // Resolve by utility method
                RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
                RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                resolveU(data, tLimit, app, 100);

                // Resolve by modified utility method
                RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
                RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                resolveUM(data, tLimit, app, 70);

                // Resolve by complete GA method
                RunConf.instance().set(CONFIG.MUTATION_PROB, .01);
                RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                resolveGA(data, tLimit, app);

                // Save information in CSV step by step.
                CSV.save(headers, data, fileName);
            }
        }

        // Show executions ends!
        System.out.println("Done!");
    }

    private static void comparisonProviders(Long seed, Integer iterations) throws IOException {
        // Get architectures
        Map<ArchitecturePattern, Double> archProb = ArchitectureProbabilities.get(List.of(
            ArchitecturePattern.values()
        ));

        // Generic variables
        List<QoS> qos = List.of(QoS.values());

        // Define limits
        int nOfServices = 500, maxProviders = 1_000, stepProviders = 100, initProviders = 100;

        // Get headers
        List<Header> headers = List.of(
            Header.RESOLVER, Header.LIMIT_TIME, Header.EXECUTION_TIME, Header.PROVIDERS, Header.SERVICES,
            Header.BEST_FITNESS, Header.MEAN_FITNESS, Header.PRE_CALCULATION_TIME, Header.GENERATIONS
        );

        // Data to save in CSV
        Map<Header, List<Object>> data = Data.getDataMap(headers);

        // Define time limit
        TimeLimit tLimit = new TimeLimit(Duration.ofSeconds(60 * 3));
        RunConf.instance().set(CONFIG.TIME_LIMIT, tLimit);

        for (int nOfProviders = initProviders; nOfProviders <= maxProviders; nOfProviders += stepProviders) {

            // Define providers range
            Range<Integer> providersRange = new Range<>(nOfProviders);

            // For each iteration
            for (int iter = 1; iter <= iterations; iter++) {
                System.out.printf("%nTime: %s%n", DATE_FORMAT.format(new Date()));
                System.out.printf("# services: %d%n", nOfServices);
                System.out.printf("# providers: %d%n", nOfProviders);
                System.out.printf("Iteration: %d/%d%n%n", iter, iterations);

                // Get providers and services
                List<Provider> providers = Providers.get(nOfProviders, qos, seed);
                List<Service> services = Services.get(nOfServices, providers, providersRange, seed);

                // Prepare an instance of application
                Application app = Applications.get(
                    providers, services, qos, seed, archProb, providersRange
                );

                // Resolve by random method
                resolveRND(seed, data, tLimit, app);

                // Resolve by express method
                resolveExpress(data, tLimit, app);

                // Resolve by utility method
                RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
                RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                resolveU(data, tLimit, app, 100);

                // Resolve by modified utility method
                RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
                RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                resolveUM(data, tLimit, app, 70);

                // Resolve by complete GA method
                RunConf.instance().set(CONFIG.MUTATION_PROB, .01);
                RunConf.instance().set(CONFIG.CONVERGENCE, .001);
                resolveGA(data, tLimit, app);

                // Save information in CSV step by step.
                CSV.save(headers, data, "comparison_providers");
            }
        }

        // Show executions ends!
        System.out.println("Done!");
    }

    private static void elbow(Long seed, Integer iterations, Boolean details) throws IOException {
        // Get architectures
        Map<ArchitecturePattern, Double> archProb = ArchitectureProbabilities.get(List.of(
            ArchitecturePattern.values()
        ));

        // Generic variables
        List<QoS> qos = List.of(QoS.values());

        // Initialize all variables
        int maxDegrees, stepDegrees, initDegrees, nOfProviders, nOfServices, nOfApplications = 10;

        // Define limits
        if (details) {
            maxDegrees = 5;
            stepDegrees = 1;
            initDegrees = 1;
        } else {
            maxDegrees = 100;
            stepDegrees = 10;
            initDegrees = 10;
        }

        // Get headers
        List<Header> headers = List.of(
            Header.RESOLVER, Header.LIMIT_TIME, Header.EXECUTION_TIME, Header.PROVIDERS, Header.SERVICES,
            Header.BEST_FITNESS, Header.MEAN_FITNESS, Header.PRE_CALCULATION_TIME, Header.U_DEGREES, Header.ID
        );

        // Data to save in CSV
        Map<Header, List<Object>> data = Data.getDataMap(headers);

        // Define time limit
        TimeLimit tLimit = new TimeLimit(Duration.ofSeconds(60 * 3));
        RunConf.instance().set(CONFIG.TIME_LIMIT, tLimit);
        RunConf.instance().set(CONFIG.MUTATION_PROB, .015);
        RunConf.instance().set(CONFIG.CONVERGENCE, .001);

        // Define random instance
        Random rnd = (seed >= 0) ? new Random(seed) : new Random();

        // Define providers range
        Range<Integer> providersRange;

        for (int id = 0; id < nOfApplications; id++) {
            nOfProviders = rnd.nextInt(900) + 100;
            nOfServices = rnd.nextInt(900) + 100;

            // Define providers range
            providersRange = new Range<>(nOfProviders);

            // Get providers and services
            List<Provider> providers = Providers.get(nOfProviders, qos, seed);
            List<Service> services = Services.get(nOfServices, providers, providersRange, seed);

            // Prepare an instance of application
            Application app = Applications.get(providers, services, qos, seed, archProb, providersRange);

            System.out.printf("Application id: %d%n%n", id);

            for (int nOfDegrees = initDegrees; nOfDegrees <= maxDegrees; nOfDegrees += stepDegrees) {
                for (int iter = 1; iter <= iterations; iter++) {
                    System.out.printf("Time: %s%n", DATE_FORMAT.format(new Date()));
                    System.out.printf("Iteration: %d/%d%n", iter, iterations);

                    // Resolve by fitness modified method
                    resolveUM(data, tLimit, app, nOfDegrees);
                    data.get(Header.U_DEGREES).add(nOfDegrees);
                    data.get(Header.ID).add(id);

                    // Resolve by fitness method
                    resolveU(data, tLimit, app, nOfDegrees);
                    data.get(Header.U_DEGREES).add(nOfDegrees);
                    data.get(Header.ID).add(id);

                    // Save information in CSV step by step.
                    String fileName;

                    if (details) {
                        fileName = "elbow_details";
                    } else {
                        fileName = "elbow";
                    }

                    System.out.println("");

                    CSV.save(headers, data, fileName);
                }
            }
        }

        // Show executions ends!
        System.out.println("Done!");
    }

    private static void resolveGA(Map<Header, List<Object>> data, TimeLimit tLimit, Application app) {
        // Add execution time
        data.get(Header.LIMIT_TIME).add(tLimit.getDuration().toMillis());

        if (data.containsKey(Header.RESOLVER)) {
            data.get(Header.RESOLVER).add("GA");
        }

        // Launch resolver
        GA.resolveByGAPair(app, data);

        System.out.println("GA done!");
    }

    private static void resolveExpress(Map<Header, List<Object>> data, TimeLimit tLimit, Application app) {
        // Add execution time
        data.get(Header.LIMIT_TIME).add(tLimit.getDuration().toMillis());

        if (data.containsKey(Header.RESOLVER)) {
            data.get(Header.RESOLVER).add("Express");
        }

        // Launch resolver
        Express.resolve(app, data);

        System.out.println("Express done!");
    }

    private static void resolveU(Map<Header, List<Object>> data, TimeLimit tLimit, Application app, Integer degrees) {
        // Add execution time
        data.get(Header.LIMIT_TIME).add(tLimit.getDuration().toMillis());

        if (data.containsKey(Header.RESOLVER)) {
            data.get(Header.RESOLVER).add(String.format("U_%d", degrees));
        }

        // Launch resolver
        Utility.resolve(app, data, degrees);

        System.out.printf("U_%d done!%n", degrees);
    }

    private static void resolveUM(Map<Header, List<Object>> data, TimeLimit tLimit, Application app, Integer degrees) {
        // Add execution time
        data.get(Header.LIMIT_TIME).add(tLimit.getDuration().toMillis());

        if (data.containsKey(Header.RESOLVER)) {
            data.get(Header.RESOLVER).add(String.format("UM_%d", degrees));
        }

        // Launch resolver
        UtilityModified.resolve(app, data, degrees);

        System.out.printf("UM_%d done!%n", degrees);
    }

    private static void resolveRND(Long seed, Map<Header, List<Object>> data, TimeLimit tLimit, Application app) {
        // Add execution time
        data.get(Header.LIMIT_TIME).add(tLimit.getDuration().toMillis());

        if (data.containsKey(Header.RESOLVER)) {
            data.get(Header.RESOLVER).add("RND");
        }

        // Resolve with random walking
        RND.resolve(app, data, seed);

        System.out.println("Random done!");
    }
}
