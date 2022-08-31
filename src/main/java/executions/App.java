package executions;

import io.jenetics.IntegerGene;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.stat.DoubleMomentStatistics;
import models.applications.Application;
import models.auxiliary.TimeLimit;
import models.enums.CONFIG;
import models.enums.Header;
import org.javatuples.Triplet;
import resolvers.DAC;
import utils.Composition;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class App {

    public static void showInformation(
        EvolutionStatistics<Double, DoubleMomentStatistics> statistics,
        EvolutionResult<IntegerGene, Double> result, Map<Integer, Integer> bestCombination, Application app
    ) {
        System.out.println(statistics);
        System.out.printf("Best phenotype: %s.%n", result.bestPhenotype());
        System.out.printf("Worst phenotype: %s.%n", result.worstPhenotype());
        System.out.printf("Global weights: %s.%n", app.getWeights());
        System.out.printf("Global constraints: %s.%n", app.getSoftConstraints());
        System.out.printf("Population: %s.%n", result.population().length());
        System.out.printf("Best composition: %s.%n", bestCombination.toString());
    }

    public static void calculateBatchesProblem(
        Application problem, Integer batchSize, Integer nOfProviders, TimeLimit timeLimit, Double mutationProbability,
        Double crossoverProbability, Number crossoverPoints, Number population, Number eliteCount,
        Map<Header, List<Object>> data, Boolean splitParallels
    ) {
        long threadId = Thread.currentThread().getId();

        // 0. Initial time counter
        Instant startInstant = Instant.now();

        // 1. Copy the app to reference
        Application copy = problem.copy();

        // 2. Resolve problem making batches and get information
        Triplet<Integer, Integer, Map<Integer, Integer>> result = DAC.resolve(
            copy, batchSize, timeLimit, splitParallels, threadId
        );
        int generations = result.getValue0();
        double nOfProblems = result.getValue1();
        Map<Integer, Integer> composition = result.getValue2();

        // 3. Calculate global composition value
        double fitness = problem.fitness(composition);

        // 4. Transform composition into legible list
        List<Integer> bestCompositionGenotypeStyle = Composition.toList(composition);

        // 5. Save data
        updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, Duration.between(startInstant, Instant.now()).toMillis()),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, nOfProviders),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, generations),
            new AbstractMap.SimpleEntry<>(Header.BATCH_SIZE, batchSize),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, bestCompositionGenotypeStyle.toString()),
            new AbstractMap.SimpleEntry<>(Header.SUB_PROBLEMS, nOfProblems),
            new AbstractMap.SimpleEntry<>(Header.LIMIT_TIME, timeLimit.getDuration().toMillis()),
            new AbstractMap.SimpleEntry<>(Header.MUTATION_PROB, mutationProbability),
            new AbstractMap.SimpleEntry<>(Header.CROSSOVER_PROB, crossoverProbability),
            new AbstractMap.SimpleEntry<>(Header.CROSSOVER_POINTS, crossoverPoints),
            new AbstractMap.SimpleEntry<>(Header.POPULATION, population),
            new AbstractMap.SimpleEntry<>(Header.ELITE_COUNT, eliteCount)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public static List<Map<CONFIG, Object>> getCombinations(Map<CONFIG, List<Object>> objects) {

        List<Map<CONFIG, Object>> combinations = new ArrayList<>();

        for (Object mutationProb : objects.get(CONFIG.MUTATION_PROB)) {
            for (Object crossoverProb : objects.get(CONFIG.CROSSOVER_PROB)) {
                for (Object crossoverPoints : objects.get(CONFIG.CROSSOVER_POINTS)) {
                    for (Object population : objects.get(CONFIG.POPULATION)) {
                        for (Object eliteCount : objects.get(CONFIG.ELITE_COUNT)) {

                            combinations.add(new HashMap<>() {{
                                put(CONFIG.MUTATION_PROB, mutationProb);
                                put(CONFIG.CROSSOVER_PROB, crossoverProb);
                                put(CONFIG.CROSSOVER_POINTS, crossoverPoints);
                                put(CONFIG.POPULATION, population);
                                put(CONFIG.ELITE_COUNT, eliteCount);
                            }});
                        }
                    }
                }
            }
        }

        return combinations;
    }

    public static void updateData(Map<Header, List<Object>> data, Map<Header, Object> information) {
        // Extract key set
        Set<Header> keys = data.keySet();

        for (Map.Entry<Header, Object> entry : information.entrySet()) {

            // If keys set hasn't the current entry-key, skip it.
            if (!keys.contains(entry.getKey())) {
                continue;
            }

            Header key = entry.getKey();
            Object val = entry.getValue();

            if (val instanceof Double) {
                val = Math.round(((double) val) * 1E5) / 1E5; // To round with 5 decimals
            }

            data.get(key).add(val);
        }
    }
}
