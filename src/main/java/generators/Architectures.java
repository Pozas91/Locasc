package generators;

import models.auxiliary.Range;
import models.enums.ArchitecturePattern;
import models.patterns.*;
import utils.Rand;

import java.util.*;

public class Architectures {
    public static Integer GATE_ID;

    /**
     * Generate a random architecture with the components given and the patterns indicated.
     *
     * @param components              List of components (Services)
     * @param probabilitiesOfPatterns Map where keys are patterns and values the probability to choose that pattern.
     * @param componentsRange         Number of components per architecture
     * @param seed                    Seed to create a random instance
     * @return A randomly generated architecture
     */
    public static Architecture get(
        List<Component> components, Map<ArchitecturePattern, Double> probabilitiesOfPatterns,
        Range<Integer> componentsRange, Long seed
    ) {
        // Define batches list variable
        List<List<Component>> batches;
        // Shuffle randomly components
        Random rnd = (seed >= 0) ? new Random(seed) : new Random();
        // Discard first random number
        rnd.nextDouble();
        // Shuffling
        Collections.shuffle(components, rnd);
        // Reset gate ID
        Architectures.GATE_ID = 0;

        while (components.size() > 1) {
            batches = toBatches(components, componentsRange, seed);
            components = convertBatchesIntoPatterns(batches, probabilitiesOfPatterns, seed);
        }

        return (Architecture) components.get(0);
    }

    public static <T> List<List<T>> toBatches(List<T> elements, Range<Integer> componentsRange, Long seed) {
        // Random
        Random rnd = (seed >= 0) ? new Random(seed) : new Random();
        // Discard first random number
        rnd.nextDouble();

        // Initialize variables
        List<List<T>> batches = new ArrayList<>();
        List<T> batch = new ArrayList<>();

        int elementsSize = elements.size(), elementsPerBatch = rnd.nextInt(
            Math.min(elementsSize, componentsRange.to() - componentsRange.from())
        ) + componentsRange.from();

        for (T c : elements) {
            if (batch.size() >= elementsPerBatch) {

                // Get another random number for other batch
                elementsPerBatch = rnd.nextInt(
                    Math.min(elementsSize, componentsRange.to() - componentsRange.from())
                ) + componentsRange.from();

                // Add sublist to
                batches.add(batch);
                // Create a new sublist
                batch = new ArrayList<>();
            }

            // Add components into current sublist
            batch.add(c);
        }

        if (batch.size() > 1) {
            // Add last batch
            batches.add(batch);
        } else {
            // Maybe is a single element, in this case simply added to previous batch
            batches.get(batches.size() - 1).add(batch.get(0));
        }

        return batches;
    }

    private static List<Component> convertBatchesIntoPatterns(
        List<List<Component>> batches, Map<ArchitecturePattern, Double> probabilitiesOfPatterns, Long seed
    ) {
        // 0. Random
        Random rnd = (seed >= 0) ? new Random(seed) : new Random();
        // Discard first random number
        rnd.nextDouble();

        // 1. Define variables
        List<Component> patterns = new ArrayList<>();
        Architecture c = null, lastC = null;

        // 2. For each batch
        for (List<Component> batch : batches) {
            if (batch.size() > 1) {
                // 3. Calculate random pattern for the roulette method
                double rNumber = rnd.nextDouble(), accumulate = 0.;

                for (Map.Entry<ArchitecturePattern, Double> pattern : probabilitiesOfPatterns.entrySet()) {
                    // Add probability to accumulate variable
                    accumulate += pattern.getValue();

                    if (rNumber <= accumulate) {
                        // 4. Get a random pattern of the list
                        switch (pattern.getKey()) {
                            case CONDITIONAL -> c = new Conditional(batch, Rand.generateProbabilities(batch.size(), rnd));
                            // Avoid 0 and 1
                            case ITERATIVE -> c = new Iterative(batch, (rnd.nextDouble() * 0.9998) + 0.0001);
                            case PARALLEL -> c = new Parallel(batch);
                            default -> {
                                // Is possible that first component of batch will be a sequential pattern,
                                // I this case we add all components for that component, extract them and add into this
                                // sequential component (FLATTEN).
                                for (int i = 0; i < batch.size(); i++) {
                                    if (batch.get(i) instanceof Sequential) {
                                        // Get sequential
                                        Sequential seq = (Sequential) batch.get(i);
                                        // Remove it from batch
                                        batch.remove(i);
                                        // Add all components
                                        batch.addAll(i, seq.getComponents());
                                        // Add information to index
                                        i += seq.getComponents().size() - 1;
                                    }
                                }

                                c = new Sequential(batch);
                            }
                        }

                        // Set input and output gates
                        c.setInputGate(Locations.getRandom(rnd));
                        c.setOutputGate(Locations.getRandom(rnd));

                        // Set gate ids
                        if (!(c instanceof Sequential)) {
                            c.setInGateID(Architectures.GATE_ID++);
                            c.setOutGateID(Architectures.GATE_ID++);
                        }

                        break;
                    }
                }

                // Update graph
                buildGraph(lastC, c);

                // 5. Check if last and current component are Sequential patterns, then we join them.
                if (lastC instanceof Sequential && c instanceof Sequential) {
                    Component a = lastC.getComponents().get(lastC.getComponents().size() - 1);
                    Component b = c.getComponents().get(0);
                    // We need link last component with next
                    a.addNext(b);

                    for (Component c2 : c.getComponents()) {
                        // Add father
                        c2.addFather(lastC);
                        // Add all components into last component
                        lastC.getComponents().add(c2);
                    }

                    // Be careful with the size of the sequential patterns and sum them.
                    lastC.incrementWeight(c.weight());
                    // We are join both components
                    lastC.clearNext();
                } else {
                    // Add new pattern to the patterns list and update lastC
                    patterns.add(c);
                    lastC = c;
                }
            } else {
                throw new IllegalArgumentException("Cannot exist batches with only one component");
            }
        }

        return patterns;
    }

    private static void buildGraph(Component lastC, Component c) {
        if ((c instanceof Sequential || c instanceof Iterative)) {
            Architecture arch = (Architecture) c;

            // All components of a sequential are consecutive
            for (int i = 1; i < arch.getComponents().size(); i++) {
                Component lC = arch.getComponents().get(i - 1);
                Component cC = arch.getComponents().get(i);

                lC.addNext(cC);
                cC.addFather(c);
            }

            // Add internal
            arch.addLink(arch.getComponents().get(0));
            arch.getComponents().get(0).addFather(arch);

        } else if (c instanceof Conditional) {
            Conditional cond = (Conditional) c;

            for (int i = 0; i < cond.getComponents().size(); i++) {
                Component branch = cond.getComponents().get(i);

                // Add internal and father links
                cond.addLink(branch);
                branch.addFather(cond);
            }

        } else if (c instanceof Parallel) {
            Parallel parallel = (Parallel) c;

            for (int i = 0; i < parallel.getComponents().size(); i++) {
                Component branch = parallel.getComponents().get(i);

                // Add internal and father links
                parallel.addLink(branch);
                branch.addFather(parallel);
            }
        }

        if (lastC instanceof Sequential || lastC instanceof Iterative) {
            lastC.addNext(c);
        }
    }
}
