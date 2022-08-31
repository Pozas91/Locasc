package executions;

import generators.Applications;
import generators.ArchitectureProbabilities;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.util.ISeq;
import models.applications.Application;
import models.auxiliary.Constraint;
import models.auxiliary.TimeLimit;
import models.enums.ArchitecturePattern;
import models.enums.ConstraintOperator;
import models.enums.Header;
import models.enums.QoS;
import problems.ApplicationProblem;
import resolvers.DAC;
import resolvers.GA;
import utils.CSV;
import utils.Data;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class HeuristicDQ {
    public static void main(String[] args) throws Exception {
        testHeuristic(-1L, 5);
    }

    public static void testHeuristic(Long seed, Integer iterations) throws IOException {
        // Get thread id
        long threadId = Thread.currentThread().getId();

        // Configure tests beds
        Map<String, Map<ArchitecturePattern, Double>> testConfiguration = new HashMap<>() {{
            put("03_heuristic_constraints_5", ArchitectureProbabilities.get());
        }};

        // Generic variables
        List<QoS> qos = List.of(QoS.COST, QoS.RESPONSE_TIME, QoS.AVAILABILITY);

        // Define time limit
        TimeLimit timeLimit = new TimeLimit(-1L, 30L);

        // Define providers
        int nOfProviders = 10;

        // Define slopes
        List<Long> slopes = List.of(2L, 5L, 60L);

        // Define top services
        int topServices = 100;

        for (Map.Entry<String, Map<ArchitecturePattern, Double>> configuration : testConfiguration.entrySet()) {
            // Data to save in CSV
            Map<Header, List<Object>> data = Data.getDataMap(all());

            for (int nOfServices = 100; nOfServices <= topServices; nOfServices += 100) {
                System.out.printf("Processing %d services...%n", nOfServices);

                for (int i = 1; i <= iterations; i++) {
                    System.out.printf("  Iteration: %d/%d%n", i, iterations);

                    // Prepare an instance of full problem
                    Application problem = Applications.get(
                        nOfProviders, nOfServices, qos, seed, configuration.getValue()
                    );

                    for (QoS attribute : problem.getAppNorm().keySet()) {
                        double minBound = problem.getAppNorm().get(attribute).getMin() * .8;
                        double maxBound = problem.getAppNorm().get(attribute).getMax() * 1.2;
                        double value = Math.random() * (maxBound - minBound) + minBound;
                        problem.putSoftConstraint(attribute, new Constraint(ConstraintOperator.LESS_THAN, value));
                    }

                    problem.setSoftConstraintsW(.2);

                    // Iterations for slopes
                    for (long slope : slopes) {
                        // Set slope time for current execution
                        timeLimit.setSlope(slope);

                        System.out.printf("    Slope: %d...%n", slope);

                        // Resolve by D&Q with parallel-division
                        DAC.resolve(problem, data, timeLimit, true, threadId);

                        // Update information
                        Duration limitTime = timeLimit.isAdaptive() ? timeLimit.getApproximateLimitTime(1) : timeLimit.getDuration();
                        data.get(Header.LIMIT_TIME).add(limitTime.toMillis());
                        updateData(data, nOfServices, timeLimit, 0, true, true);

                        // Get and clean result from D&Q to using it as heuristic in genetic algorithm
                        ISeq<Genotype<IntegerGene>> population = getAndCleanPopulation(data, problem);

                        // Resolve a complete genetic algorithm with heuristic (GAH)
                        GA.resolveByGA(problem, data, population);

                        // Update information
                        limitTime = timeLimit.isAdaptive() ? timeLimit.getApproximateLimitTime(nOfServices) : timeLimit.getDuration();
                        data.get(Header.LIMIT_TIME).add(limitTime.toMillis());
                        updateData(data, nOfServices, timeLimit, 1, false, true);

                        // Resolve a complete genetic algorithm without heuristic
                        GA.resolveByGA(problem, data);

                        // Update information
                        limitTime = timeLimit.isAdaptive() ? timeLimit.getApproximateLimitTime(nOfServices) : timeLimit.getDuration();
                        data.get(Header.LIMIT_TIME).add(limitTime.toMillis());
                        updateData(data, nOfServices, timeLimit, 0, false, true);

                        // Saving data
                        CSV.save(all(), data, configuration.getKey());
                    }
                }
            }
        }
    }

    public static ISeq<Genotype<IntegerGene>>
    getAndCleanPopulation(Map<Header, List<Object>> data, Application problem) {
        List<Integer> lastGenotype = (List<Integer>) data.get(Header.GENOTYPE).get(data.get(Header.GENOTYPE).size() - 1);

        // Get a copy of the genotype of the current problem
        ISeq<IntegerChromosome> rawChromosome = ApplicationProblem.getChromosomes(problem);

        // Convert the winner genotype into the best genotype
        ISeq<IntegerChromosome> bestChromosome = IntStream.range(0, rawChromosome.size())
            .mapToObj(i -> {
                IntegerGene cGene = rawChromosome.get(i).get(0);
                List<Integer> candidates = problem.getService(i).getCandidates();
                int candidateIndex = candidates.indexOf(lastGenotype.get(i));
                return IntegerChromosome.of(cGene.newInstance(candidateIndex));
            })
            .collect(ISeq.toISeq());

        List<Genotype<IntegerGene>> individual = List.of(Genotype.of(bestChromosome));
        return ISeq.of(individual);
    }

    private static void updateData(
        Map<Header, List<Object>> data, Integer nOfServices, TimeLimit timeLimit, Integer heuristic,
        Boolean splitParallels, Boolean variableProviders
    ) {
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.SERVICES, nOfServices),
            new AbstractMap.SimpleEntry<>(Header.SLOPE, timeLimit.getSlope()),
            new AbstractMap.SimpleEntry<>(Header.INTERCEPT, timeLimit.getIntercept()),
            new AbstractMap.SimpleEntry<>(Header.HEURISTIC, heuristic),
            new AbstractMap.SimpleEntry<>(Header.SPLIT_PARALLELS, splitParallels ? 1 : 0),
            new AbstractMap.SimpleEntry<>(Header.VARIABLE_PROVIDERS, variableProviders ? 1 : 0)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public static List<Header> all() {
        return Arrays.asList(
//            Header.SERVICES, Header.PROVIDERS, Header.BATCH_SIZE, Header.LIMIT_TIME, Header.SLOPE, Header.INTERCEPT,
//            Header.GENERATIONS, Header.EXECUTION_TIME, Header.SUB_PROBLEMS, Header.WORST_FITNESS,
//            Header.MEAN_FITNESS, Header.BEST_FITNESS, Header.GENOTYPE, Header.SPLIT_PARALLELS,
//            Header.VARIABLE_PROVIDERS
        );
    }
}
