package resolvers;

import executions.App;
import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.Constraint;
import models.auxiliary.Normalization;
import models.auxiliary.TimeLimit;
import models.enums.ConstraintOperator;
import models.enums.Header;
import models.enums.ObjectiveFunction;
import models.enums.QoS;
import models.patterns.*;
import org.javatuples.Triplet;
import utils.Composition;
import utils.Fakes;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DAC {
    /**
     * These methods are used to divide-and-conquer an application for resolve it per batches.
     *
     * @param batchSize Size of each batch
     * @return A triplet which meaning is (generations, nOfProblems, composition).
     */
    private static Triplet<Integer, Integer, Map<Integer, Integer>>
    dac(Application app, Integer batchSize, TimeLimit timeLimit, Boolean splitParallels, Long threadId) {
        // 1. Define variables
        List<Component> batch = new ArrayList<>(), finalBatch = new ArrayList<>();
        int generations = 0;
        Map<Integer, Integer> composition = new HashMap<>(), partialComposition;
        Triplet<Integer, Integer, Map<Integer, Integer>> result;

        int cBatchSize = 0, archWeight = app.getArchitecture().weight(), archProcessed = 0,
            lowLazyBatchSize = (int) Math.floor(batchSize * 0.6), nOfProblems = 0;

        // 2. We get components, check if all components are base, if this happens, then isn't necessary to order
        // (size of all are 1)
        List<Component> components = app.getArchitecture().getComponents();

        if (components.size() != archWeight) {
            // Some components not are services, it's better to order
            components.sort(Comparator.reverseOrder());
        }

        // Get length of the component list
        int cLength = components.size();

        // 3. For each all components
        for (int i = 0; i < cLength; i++) {
            // 3.1. Extract component and get it's size.
            Component c = components.get(i);
            int cWeight = c.weight();

            // 3.2. Check possible cases
            if (cWeight >= batchSize) {
                // 3.2.1. The component is so big, then we can work with it as independent problem
                Application subProblem = app.getSubProblem(c.getArchitecture());
                result = DAC.resolve(subProblem, batchSize, timeLimit, splitParallels, threadId);

                generations += result.getValue0();
                nOfProblems += result.getValue1();
                partialComposition = new HashMap<>(result.getValue2());
                composition.putAll(partialComposition);

                // Resolved services must be removed from services to explore
                partialComposition.keySet().parallelStream().forEach(key -> app.getServicesToExplore().remove(key));

            } else if ((archWeight - archProcessed) <= lowLazyBatchSize) {
                // 3.2.2. If the rest of the architecture is lower than lowLazyBatchSize we can process them all
                // together (we need before check two situations)

                if (cBatchSize >= lowLazyBatchSize && ((archWeight - archProcessed) >= lowLazyBatchSize)) {
                    // 3.2.2.1. We can divide the problem into two sub-problems of similar size and resolve them in two
                    // steps

                    // Resolve components (from last component to current - 1)
                    result = resolveSubArchitecture(app, batchSize, batch, timeLimit, splitParallels, threadId);

                    generations += result.getValue0();
                    nOfProblems += result.getValue1();
                    partialComposition = new HashMap<>(result.getValue2());
                    composition.putAll(partialComposition);

                    // Resolved services must be removed from services to explore
                    partialComposition.keySet().parallelStream().forEach(key -> app.getServicesToExplore().remove(key));

                    // Resolve components (from current component to last component)
                    result = resolveSubArchitecture(
                        app, batchSize, components.subList(i, cLength), timeLimit, splitParallels, threadId
                    );

                    generations += result.getValue0();
                    nOfProblems += result.getValue1();
                    partialComposition = new HashMap<>(result.getValue2());
                    composition.putAll(partialComposition);

                    // Resolved services must be removed from services to explore
                    partialComposition.keySet().parallelStream().forEach(key -> app.getServicesToExplore().remove(key));

                } else {
                    // 3.2.2.2. Resolve components pending in the batch and rest of components of this architecture all
                    // together
                    finalBatch.addAll(batch);
                    finalBatch.addAll(components.subList(i, cLength));

                    result = resolveSubArchitecture(app, batchSize, finalBatch, timeLimit, splitParallels, threadId);

                    generations += result.getValue0();
                    nOfProblems += result.getValue1();
                    partialComposition = new HashMap<>(result.getValue2());
                    composition.putAll(partialComposition);

                    // Resolved services must be removed from services to explore
                    partialComposition.keySet().parallelStream().forEach(key -> app.getServicesToExplore().remove(key));
                }

                // Remove processed batch
                batch = new ArrayList<>();

                // End of loop (not necessary continue)
                break;

            } else if (cWeight + cBatchSize <= batchSize) {
                // 3.2.3. If we can add more components into current batch, then do it.
                batch.add(c);
                cBatchSize += cWeight;
            } else {
                // 3.2.4. If we cannot add more components into current batch, then resolve the batch, and later create
                // a new batch
                result = resolveSubArchitecture(app, batchSize, batch, timeLimit, splitParallels, threadId);

                generations += result.getValue0();
                nOfProblems += result.getValue1();
                partialComposition = new HashMap<>(result.getValue2());
                composition.putAll(partialComposition);

                // Resolved services must be removed from services to explore
                partialComposition.keySet().parallelStream().forEach(key -> app.getServicesToExplore().remove(key));

                cBatchSize = cWeight;
                batch = new ArrayList<>(Collections.singletonList(c));
            }

            // Calculate the progress of batching
            archProcessed += cWeight;
        }

        // 4. If keep any component into batch, then resolve to finish
        if (batch.size() > 0) {
            result = resolveSubArchitecture(app, batchSize, batch, timeLimit, splitParallels, threadId);

            generations += result.getValue0();
            nOfProblems += result.getValue1();
            partialComposition = new HashMap<>(result.getValue2());
            composition.putAll(partialComposition);

            // Resolved services must be removed from services to explore
            partialComposition.keySet().parallelStream().forEach(key -> app.getServicesToExplore().remove(key));
        }

        return new Triplet<>(generations, nOfProblems, composition);
    }

    /**
     * Recursive function to split complex architectures to resolve later
     *
     * @param batchSize Size of each split
     * @return List of <Problem, PartsOfProblemPending, WhereIsPlaceThatProblem, WhichBelow>
     */
    public static Triplet<Integer, Integer, Map<Integer, Integer>>
    resolve(Application app, Integer batchSize, TimeLimit timeLimit, Boolean splitParallels, Long threadId) {
        int nLazySize, pLazySize;

        // 1. Define variables
        if (batchSize == 1) {
            nLazySize = 1;
            pLazySize = 1;
        } else {
            nLazySize = (int) (batchSize * 1.6); // Lazy size for normal patterns
            pLazySize = batchSize * 3; // Lazy size for parallels patterns
        }

        // Get architecture from this app
        Architecture architecture = app.getArchitecture();

        // Get current size of this architecture
        int architectureWeight = architecture.weight();

        // 2. Resolve current application
        if (architectureWeight == 1) {
            // 2.0. Resolve single services (We can resolve by exact method)
            return resolveExactMethod(app);
        } else if (architecture instanceof Parallel && architectureWeight > pLazySize) {
            // 2.1. First base case, the architecture is parallel and divide it can improve the performance
            //  - It's greater than `pLazySize`
            if (splitParallels) {
                return resolveParallelApplication(app, batchSize, timeLimit, threadId);
            } else {
                return GA.resolve(app);
            }
        } else if (architecture instanceof Parallel || (architectureWeight <= nLazySize)) {
            // 2.2. Second base case, the application doesn't need to be divided
            //  - Parallels with a size lower than `pLazySize`
            //  - Little architectures with a size equal or lower than `nLazySize`.
            return GA.resolve(app);
        } else {
            /*
             * 2.3.1. Split sequential pattern: Do batches of batchSize indicate
             * 2.3.2. Split iterative pattern: Same that before
             * 2.3.3. Split conditional pattern: Same that sequential pattern, but we need sum probabilities of the new
             *    batch, and them create another conditional with the probabilities fixed, p.e.:
             *
             *  - Example :> 0.4 * 2 + 0.4 * 5 + 0.2 * 21 -> 7
             *  - Join two first :> 0.8 * ((0.4/0.8) * 2 + ((0.4/0.8) * 5)) + 0.2 * 21 -> 7
             */
            return dac(app, batchSize, timeLimit, splitParallels, threadId);
        }
    }

    /**
     * Resolve a part of sub-architecture and return the information
     *
     * @param batchSize Size of the batch desired
     * @param batch     Batch of components
     * @return A triplet which means (generations, subProblems, composition)
     */
    private static Triplet<Integer, Integer, Map<Integer, Integer>> resolveSubArchitecture(
        Application app, Integer batchSize, List<Component> batch, TimeLimit timeLimit, Boolean splitParallels,
        Long threadId
    ) {
        Architecture newArchitecture;

        if (app.getArchitecture() instanceof Conditional) {
            // The unique difference between a sequential or iterative pattern with the conditional pattern is that in
            // the conditional pattern we must sum probabilities.
            List<Double> subProbabilities = new ArrayList<>();
            List<Double> probabilities = ((Conditional) app.getArchitecture()).getProbabilities();

            if (batch.size() > 1) {
                double sum = 0;

                // Get probabilities for the components get them and calculate the sum of those probabilities.
                for (Component component : batch) {
                    int index = app.getArchitecture().getComponents().indexOf(component);
                    double probability = probabilities.get(index);
                    subProbabilities.add(probability);
                    sum += probability;
                }

                // Modify all probabilities with the sum
                for (int i = 0; i < subProbabilities.size(); i++) {
                    subProbabilities.set(i, subProbabilities.get(i) / sum);
                }
            } else {
                // It's possible that appears a conditional with only a branch, in this case we can use the sequential
                // resolve or create a conditional with 100% probability, both solutions works. In our case, we use a
                // 100% branch conditional to keep same information
                subProbabilities = Collections.singletonList(1.);
            }

            newArchitecture = new Conditional(batch, subProbabilities);
        } else {
            newArchitecture = new Sequential(batch);
        }

        Application subProblem = app.getSubProblem(newArchitecture);

        return resolve(subProblem, batchSize, timeLimit, splitParallels, threadId);
    }

    /**
     * Resolve the problem with the exact method, with the next conditions:
     * - The application must have only a single component.
     * - We must get the original value for the provider
     *
     * @return A triplet which means: (generations, nOfProblems, composition)
     * - Generations is always 1, because we only do a single iteration over each provider (don't divide)
     * - NOfProblems is always 1, because we only do a single iteration over each provider (don't divide)
     * - Composition is a map, where key is the single service that we receive at beginning of method, and value is the
     * global position of the provider in general provider's list.
     * <p>
     * Notice than in this case, generations must be equals to nOfProblems
     */
    private static Triplet<Integer, Integer, Map<Integer, Integer>> resolveExactMethod(Application app) {
        long threadId = Thread.currentThread().getId();

        // 1.1. In this situation we have an application with only a service to explore, so get it.
        Map.Entry<Integer, Integer> serviceToExplore = app.getServicesToExplore().entrySet().iterator().next();
        IndexService iService = app.getArchitecture().getIndexServices().get(0);

        if (!iService.getIService().equals(serviceToExplore.getKey())) {
            throw new RuntimeException("Something wrong with services to explore");
        }

        Service service = iService.getService(app);

        // 1.2. Define useful variables
        int bestCandidate = -1, softConstraintsFailed;
        double bestFitness = -1., fitness;

        // 2. For each provider available in this service
        for (int iProviderPosition = 0; iProviderPosition < service.getCandidates().size(); iProviderPosition++) {
            // Set the provider position of candidates list
            app.setServiceComposition(threadId, iService.getIService(), iProviderPosition);

            // Create
            softConstraintsFailed = 0;
            fitness = 0.;

            for (Map.Entry<QoS, Double> entry : app.getWeights().entrySet()) {
                // Extract information
                QoS qos = entry.getKey();
                double weight = entry.getValue();
                // Get value and normalized value
                double val = app.getArchitecture().value(app, qos, threadId);
                // Extract constraints
                Constraint
                    softConstraint = app.getSoftConstraints().get(qos),
                    hardConstraint = app.getHardConstraints().get(qos);

                // If this provider doesn't satisfy hardConstraint then return 0 and break loop.
                if (hardConstraint != null && hardConstraint.isInvalid(val)) {
                    fitness = 0.;
                    break;
                }

                // Sum soft constraints if failed
                if (softConstraint != null && softConstraint.isInvalid(val)) {
                    softConstraintsFailed++;
                }

                Normalization norm = app.getAppNorm().get(qos);
                boolean toMinimize = qos.getObjective() == ObjectiveFunction.MINIMIZE;
                double nVal = norm.normalize(val, toMinimize, app.getMethod());

                fitness += nVal * weight;
            }

            if (fitness >= 0.) {
                // Add penalization by constraints failed
                int n = (1 - (softConstraintsFailed / Math.max(app.getSoftConstraints().size(), 1)));

                // f = (n * penalty) + ((1 - penalty) * f)
                fitness = (n * app.getSoftConstraintsW()) + ((1 - app.getSoftConstraintsW()) * fitness);

                if (fitness > bestFitness) {
                    bestCandidate = service.getCandidate(iProviderPosition);
                    bestFitness = fitness;
                }
            }
        }

        return new Triplet<>(1, 1, Collections.singletonMap(serviceToExplore.getKey(), bestCandidate));
    }

    /**
     * Method to resolve parallel applications
     * <p>
     * IMPORTANT: We suppose that we have more than RESPONSE_TIME attribute, if not this operation hasn't sense.
     *
     * @param batchSize Size of each batch
     * @param timeLimit Time limit
     * @return A triplet which meaning is (generations, nOfProblems, composition)
     */
    private static Triplet<Integer, Integer, Map<Integer, Integer>> resolveParallelApplication(
        Application app, Integer batchSize, TimeLimit timeLimit, Long threadId
    ) {
        // 1. Separate each branch into a single application
        Set<Application> parallelApps = app.getArchitecture().getComponents().stream()
            // Be careful with this stream, if we use a parallel stream at this point, the information could be wrong
            .map(c -> {
                // Definitively we need re-normalize for each sub-application to keep the proportionality
                Application appSub = app.getSubProblem(c.getArchitecture());
                appSub.updateAppNormalization();
                return appSub;
            }).collect(Collectors.toSet());

        // 2. Prepare variables
        Application criticalPathApp = null;
        Map<Application, Map<Integer, Integer>> resultsOfEachBranch = new HashMap<>();
        Map<Integer, Integer> composition = new HashMap<>();

        int nOfProblems = 0, generations = 0;
        double criticalPathTime = Double.MIN_VALUE;

        // 3. Run each branch independent to find critical path (trying optimizing each execution)
        for (Application parallelApp : parallelApps) {
            // 3.1. Resolve this app in isolation and get its generations, nOfProblems and composition
            Triplet<Integer, Integer, Map<Integer, Integer>> result = resolve(
                parallelApp, batchSize, timeLimit, true, threadId
            );
            // 3.2. With before composition extract a provider from the parallel app to replace later
            Provider fakeProvider = Fakes.newProvider(parallelApp, result.getValue2(), threadId);
            // 3.3. Check if current response time is greater than previous, and save it if is necessary
            if (fakeProvider.getAttributeValue(QoS.RESPONSE_TIME) > criticalPathTime) {
                criticalPathApp = parallelApp;
                criticalPathTime = fakeProvider.getAttributeValue(QoS.RESPONSE_TIME);
            }
            // 3.4. Save results
            resultsOfEachBranch.put(parallelApp, result.getValue2());
            generations += result.getValue0();
            nOfProblems += result.getValue1();
        }

        // 4. Recalculate others branches, with weight for RESPONSE_TIME equals to zero, and hard constraint equals to
        // criticalPathTime.

        // 4.1. Removing critical path to avoid repeat unnecessary executions
        parallelApps.remove(criticalPathApp);

        // 4.2. For the rest of branches, repeat the executions with the new constraints and weights
        for (Application parallelApp : parallelApps) {
            // 4.2.1. Set a hard constraints where f(x) of all compositions with a higher response time that
            // criticalPathTime is 0
            Constraint hardConstraint = new Constraint(ConstraintOperator.LESS_THAN, criticalPathTime);
            parallelApp.putHardConstraints(QoS.RESPONSE_TIME, hardConstraint);

            // 4.2.2. Set zero weight for response time, and distribute it weights to another weights scaling with the
            // new limit
            double responseTimeW = parallelApp.getWeights().get(QoS.RESPONSE_TIME), newLimit = 1. - responseTimeW;

            for (QoS key : parallelApp.getWeights().keySet()) {
                parallelApp.getWeights().put(key, parallelApp.getWeights().get(key) / newLimit);
            }

            // Set RESPONSE_TIME to zero
            parallelApp.putWeights(QoS.RESPONSE_TIME, 0.);

            // 4.2.3. Optimize the app
            Triplet<Integer, Integer, Map<Integer, Integer>> result = resolve(
                parallelApp, batchSize, timeLimit, true, threadId
            );
            resultsOfEachBranch.put(parallelApp, result.getValue2());
            generations += result.getValue0();
            nOfProblems += result.getValue1();
        }

        // Update compositions with each branch composition
        for (Map<Integer, Integer> localComposition : resultsOfEachBranch.values()) {
            composition.putAll(localComposition);
        }

        return new Triplet<>(generations, nOfProblems, composition);
    }

    /**
     * Method to resolve an application making batches and give statistics information
     *
     * @param batchSize Size of batches desired
     * @param data      A map with statistics information about resolution of this application
     */
    public static void resolve(
        Application app, Integer batchSize, Map<Header, List<Object>> data, TimeLimit timeLimit, Boolean splitParallels,
        Long threadId
    ) {
        // 0. Initial time counter
        Instant startInstant = Instant.now();
        // 1. Make an app copy to work with that
        Application copy = app.copy();
        // 2. Define full problem
        Triplet<Integer, Integer, Map<Integer, Integer>> result = resolve(
            copy, batchSize, timeLimit, splitParallels, threadId
        );

        // 3. Extract information
        int generations = result.getValue0(), nOfSubProblems = result.getValue1();
        Map<Integer, Integer> composition = result.getValue2();

        // 4. Not should be necessary, but we go to check if composition size is equals to servicesToExplore size.
        if (composition.size() != app.getServicesToExplore().size()) {
            throw new IllegalArgumentException(
                String.format(
                    "Something wrong happens! %s services are disappear.",
                    (app.getServicesToExplore().size() - composition.size())
                )
            );
        }

        // 5. Calculate global composition value
        double fitness = app.fitness(composition);

        // 6. Transform composition into legible list
        List<Integer> listComposition = Composition.toList(composition);
        double executionTime = Duration.between(startInstant, Instant.now()).toMillis();

        // 7. Save only data needed
        App.updateData(data, Stream.of(
            new AbstractMap.SimpleEntry<>(Header.EXECUTION_TIME, executionTime),
            new AbstractMap.SimpleEntry<>(Header.PROVIDERS, app.getProviders().size()),
            new AbstractMap.SimpleEntry<>(Header.GENERATIONS, generations),
            new AbstractMap.SimpleEntry<>(Header.BATCH_SIZE, batchSize),
            new AbstractMap.SimpleEntry<>(Header.BEST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.MEAN_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.WORST_FITNESS, fitness),
            new AbstractMap.SimpleEntry<>(Header.GENOTYPE, listComposition),
            new AbstractMap.SimpleEntry<>(Header.SUB_PROBLEMS, nOfSubProblems)
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Resolve an application using divide and conquer algorithm
     */
    public static void resolve(
        Application app, Map<Header, List<Object>> data, TimeLimit tLimit, Boolean splitParallels, Long threadId
    ) {
        resolve(app, 1, data, tLimit, splitParallels, threadId);
    }
}