package resolvers;

import executions.App;
import models.applications.Application;
import models.applications.Gate;
import models.applications.Service;
import models.enums.Header;
import org.javatuples.Pair;
import utils.Composition;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RND {

    public static void resolve(Application app, Map<Header, List<Object>> data, Long seed) {
        // 0. Initial time counter
        Instant startInstant = Instant.now();
        // 1. Run randomly resolution
        Map<Integer, Integer> rawComposition = new HashMap<>();

        // 2. Prepare random
        Random rnd = (seed >= 0) ? new Random(seed) : new Random();

        for (Map.Entry<Integer, Integer> e : app.getServicesToExplore().entrySet()) {
            Integer iS = e.getKey(), iGenotype = e.getValue();
            Service s = app.getService(iS);
            int iSelected = rnd.nextInt(s.getCandidates().size());
            rawComposition.put(iGenotype, s.getCandidate(iSelected));
        }

        for (Map.Entry<Integer, Integer> e : app.getGatesToExplore().entrySet()) {
            Integer iG = e.getKey(), iGenotype = e.getValue();
            Gate g = app.getGate(iG);
            int iSelected = rnd.nextInt(g.getCandidates().size());
            rawComposition.put(iGenotype, g.getCandidate(iSelected));
        }

        // Extract real genotype
        List<Integer> composition = Composition.toList(rawComposition);

        // 3. Get duration
        long executionTime = Duration.between(startInstant, Instant.now()).toMillis();

        // 4. Get fitness
        double fitness = Application.fitnessPair(new Pair<>(app, composition));

        // 5. Update information
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.PRE_CALCULATION_TIME, 0),
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, executionTime),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, app.getProviders().size()),
            new AbstractMap.SimpleEntry<>(Header.SERVICES, app.getServices().size()),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, 1),
            new AbstractMap.SimpleEntry<>(Header.BATCH_SIZE, 0),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, composition),
            new AbstractMap.SimpleEntry<>(Header.SUB_PROBLEMS, 1)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
