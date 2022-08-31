package generators;

import models.enums.ArchitecturePattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchitectureProbabilities {

    public static Map<ArchitecturePattern, Double> get(List<ArchitecturePattern> patterns, List<Double> probabilities) {
        Map<ArchitecturePattern, Double> map = new HashMap<>();

        for (int i = 0; i < probabilities.size(); i++) {
            map.put(patterns.get(i), probabilities.get(i));
        }

        return map;
    }

    /**
     * Return map where each key is an architecture pattern indicated and all patterns have same probability
     *
     * @param patterns List of patterns
     * @return Return a map with the probabilities per architecture
     */
    public static Map<ArchitecturePattern, Double> get(List<ArchitecturePattern> patterns) {
        int size = patterns.size();
        double probability = 1. / size;

        return get(patterns, Collections.nCopies(size, probability));
    }

    /**
     * Basic and egalitarian
     *
     * @return Return a map with the probabilities per architecture
     */
    public static Map<ArchitecturePattern, Double> get() {
        return get(List.of(ArchitecturePattern.values()));
    }
}
