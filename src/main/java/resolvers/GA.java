package resolvers;

import executions.App;
import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.stat.DoubleMomentStatistics;
import io.jenetics.util.ISeq;
import models.applications.Application;
import models.auxiliary.TimeLimit;
import models.enums.CONFIG;
import models.enums.Header;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.jetbrains.annotations.NotNull;
import problems.ApplicationProblem;
import problems.GeneralProblem;
import problems.PairProblem;
import utils.Composition;
import utils.RunConf;

import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GA {
    /**
     * Method to resolve an application and give statistics information
     *
     * @param data A map with statistics information about resolution of this application
     */
    public static void resolveByGA(
        Application app, Map<Header, List<Object>> data, ISeq<Genotype<IntegerGene>> population
    ) {
        // 0. Initial time counter
        Instant startInstant = Instant.now();
        // 1. Run genetic algorithm and get data
        Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>> pair;
        pair = GA.resolveByGA(app, population);
        // 2. Decompose results
        EvolutionResult<IntegerGene, Double> result = pair.getValue0();
        EvolutionStatistics<Double, DoubleMomentStatistics> statistics = pair.getValue1();

        DoubleMomentStatistics fitness = statistics.fitness();
        double maxFitness = fitness.max(), minFitness = fitness.min(), meanFitness = fitness.mean();

        // Get genotype
        Genotype<IntegerGene> genotype = result.bestPhenotype().genotype();

        // Convert genotype into a legible composition
        List<Integer> legibleComposition = Composition.toList(genotype, app);
        long executionTime = Duration.between(startInstant, Instant.now()).toMillis();

        // 3. Update information
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, executionTime),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, app.getProviders().size()),
            new AbstractMap.SimpleEntry<>(Header.SERVICES, app.getServices().size()),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, statistics.evolveDuration().result().count()),
            new AbstractMap.SimpleEntry<>(Header.BATCH_SIZE, 0),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, maxFitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, meanFitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, minFitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, legibleComposition),
            new AbstractMap.SimpleEntry<>(Header.SUB_PROBLEMS, 1)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public static void resolveByGAPair(
        Application app, Map<Header, List<Object>> data, ISeq<Genotype<IntegerGene>> population
    ) {
        // 0. Initial time counter
        Instant startInstant = Instant.now();
        // 1. Run genetic algorithm and get data
        Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>> pair;
        pair = GA.resolveByGAPair(app, population);
        // 2. Decompose results
        EvolutionResult<IntegerGene, Double> result = pair.getValue0();
        EvolutionStatistics<Double, DoubleMomentStatistics> statistics = pair.getValue1();

        DoubleMomentStatistics fitness = statistics.fitness();
        double maxFitness = fitness.max(), minFitness = fitness.min(), meanFitness = fitness.mean();

        // Get genotype
        Genotype<IntegerGene> genotype = result.bestPhenotype().genotype();

        // Convert genotype into a legible composition
        List<Integer> legibleComposition = Composition.toList(genotype, app);
        long executionTime = Duration.between(startInstant, Instant.now()).toMillis();

        // 3. Update information
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.PRE_CALCULATION_TIME, 0),
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, executionTime),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, app.getProviders().size()),
            new AbstractMap.SimpleEntry<>(Header.SERVICES, app.getServices().size()),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, statistics.evolveDuration().result().count()),
            new AbstractMap.SimpleEntry<>(Header.BATCH_SIZE, 0),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, maxFitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, meanFitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, minFitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, legibleComposition),
            new AbstractMap.SimpleEntry<>(Header.SUB_PROBLEMS, 1)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public static void resolveByGA(Application app, Map<Header, List<Object>> data) {
        GA.resolveByGA(app, data, ISeq.of());
    }

    public static void resolveByGAPair(Application app, Map<Header, List<Object>> data) {
        GA.resolveByGAPair(app, data, ISeq.of());
    }

    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    resolveByGA(Application app) {
        return resolveByGA(app, ISeq.of());
    }

    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    resolveByGA(Application app, ISeq<Genotype<IntegerGene>> population) {
        return prepareEngine(new ApplicationProblem(app), app, population);
    }

    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    resolveByGAPair(Application app, ISeq<Genotype<IntegerGene>> population) {
        return prepareEngine(new PairProblem(app), app, population);
    }

    @NotNull
    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    prepareEngine(GeneralProblem<?> problem, Application app, ISeq<Genotype<IntegerGene>> population) {
        // Extract configuration
        Map<CONFIG, Object> conf = RunConf.instance().get();

        final Mutator<?, ?> mutator = new Mutator<>(
            (Double) conf.getOrDefault(CONFIG.MUTATION_PROB, .03)
        );
        final Predicate<EvolutionResult<?, Double>> populationConvergence = Limits.byPopulationConvergence(
            (Double) conf.getOrDefault(CONFIG.CONVERGENCE, .001)
        );

        final Integer populationSize = (Integer) conf.getOrDefault(CONFIG.POPULATION, 100);
//        final Integer steadyGenerations = (Integer) conf.getOrDefault(CONFIG.STEADY_GENERATIONS, 500);
        final Integer survivorsSize = (Integer) conf.getOrDefault(CONFIG.SURVIVORS_SIZE, 10);
        final Boolean showResults = (Boolean) conf.getOrDefault(CONFIG.SHOW_RESULTS, false);
        final TimeLimit tLimit = (TimeLimit) conf.getOrDefault(CONFIG.TIME_LIMIT, new TimeLimit(Duration.ofSeconds(60)));
        final UniformCrossover<?, ?> crossover = new UniformCrossover<>();

        if (tLimit.isAdaptive()) {
            // A way to indicated that providers are more important than services in search space (this not really searchSpace)
            final int nOfNodes = app.getServicesToExplore().size() + app.getGatesToExplore().size();
            tLimit.calcAdaptiveTime(nOfNodes);
        }

        // 3. Create the execution environment
        final Engine.Builder<IntegerGene, Double> engineBuilder = Engine.builder(problem)
            // Setting initial population
            .populationSize(populationSize)
            .survivorsSize(survivorsSize)
            .selector(new TruncationSelector(survivorsSize))
            // Use all available cores
            .executor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()))
            // Define alters
            .alterers(
                // This mutator type is the best option in general for mutate chromosomes
                crossover, mutator
            );

        final Engine<IntegerGene, Double> engine = engineBuilder.maximizing().build();

        // Statistics resume
        final EvolutionStatistics<Double, DoubleMomentStatistics> statistics = EvolutionStatistics.ofNumber();

        // Define result variable
        EvolutionStream<IntegerGene, Double> engineStream;
        engineStream = (population.size() > 0) ? engine.stream(population) : engine.stream();

        // 4. Start the execution (evolution) and collect the result.
        EvolutionResult<IntegerGene, Double> result = engineStream
            // Define limits
            .limit(Limits.byExecutionTime(tLimit.getDuration()))
            .limit(populationConvergence)
//            .limit(Limits.bySteadyFitness(steadyGenerations))
            // Peek this generation into statistics
            .peek(statistics).collect(EvolutionResult.toBestEvolutionResult());

        // Get best combination
        final Genotype<IntegerGene> bestCombination = result.bestPhenotype().genotype();

        // 5. Show information
        if (showResults) {
            // We need reformat genotype to respect different providers
            Map<Integer, Integer> realComposition = Composition.toMap(bestCombination, app);
            App.showInformation(statistics, result, realComposition, app);
        }

        return new Pair<>(result, statistics);
    }

    @NotNull
    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    prepareEngine(GeneralProblem<?> problem, Application app) {
        return prepareEngine(problem, app, ISeq.of());
    }

    /**
     * Resolve a case base of an application that cannot be divided
     *
     * @return A triplet which next meaning (generations, nOfProblems, composition)
     */
    public static Triplet<Integer, Integer, Map<Integer, Integer>> resolve(Application app) {
        // 1. Resolve this application
        Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>> pair;
        pair = GA.resolveByGA(app);

        // 2. Decompose results
        EvolutionResult<IntegerGene, Double> result = pair.getValue0();
        EvolutionStatistics<Double, DoubleMomentStatistics> statistics = pair.getValue1();
        Phenotype<IntegerGene, Double> bestPhenotype = result.bestPhenotype();

        // 3. Update generations done
        int generations = Math.toIntExact(statistics.evolveDuration().result().count());

        // 4. Get real composition
        Map<Integer, Integer> realComposition = Composition.toMap(bestPhenotype.genotype(), app);

        return new Triplet<>(generations, 1, realComposition);
    }
}
