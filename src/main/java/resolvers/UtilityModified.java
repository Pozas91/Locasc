package resolvers;

import executions.App;
import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.stat.DoubleMomentStatistics;
import io.jenetics.util.ISeq;
import models.applications.Application;
import models.applications.UMApplication;
import models.enums.Header;
import org.javatuples.Pair;
import problems.UMProblem;
import utils.Composition;

import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UtilityModified {
    public static void resolve(Application app, Map<Header, List<Object>> data, Integer degrees) {
        resolve(app, data, ISeq.of(), degrees);
    }

    public static void resolve(
        Application app, Map<Header, List<Object>> data, ISeq<Genotype<IntegerGene>> population, Integer degrees
    ) {
        // 0. Initial time counter
        Instant start = Instant.now();
        // 1. Create fitness application and fitness problem
        UMApplication uApp = new UMApplication(app, degrees);
        long preExecution = Duration.between(start, Instant.now()).toMillis();
        start = Instant.now();
        // 2. Run genetic algorithm and get data
        Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>> pair;
        pair = UtilityModified.resolve(uApp, population);
        // 3. Decompose results
        EvolutionResult<IntegerGene, Double> result = pair.getValue0();
        EvolutionStatistics<Double, DoubleMomentStatistics> statistics = pair.getValue1();
        // 4. Work with the genotype
        Map<Integer, Integer> rawComposition = uApp.getSimilarProviders(result);
        // Convert to list composition
        List<Integer> composition = Composition.toList(rawComposition);
        long executionTime = Duration.between(start, Instant.now()).toMillis();

        // 5. Calculate fitness
        double fitness = Application.fitnessPair(new Pair<>(uApp, composition));

        // 6. Update information
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.PRE_CALCULATION_TIME, preExecution),
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, executionTime),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, app.getProviders().size()),
            new AbstractMap.SimpleEntry<>(Header.SERVICES, app.getServices().size()),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, statistics.evolveDuration().result().count()),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, composition)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    resolve(UMApplication uApp) {
        return resolve(uApp, ISeq.of());
    }

    public static Pair<EvolutionResult<IntegerGene, Double>, EvolutionStatistics<Double, DoubleMomentStatistics>>
    resolve(UMApplication uApp, ISeq<Genotype<IntegerGene>> population) {
        return GA.prepareEngine(new UMProblem(uApp), uApp, population);
    }
}
