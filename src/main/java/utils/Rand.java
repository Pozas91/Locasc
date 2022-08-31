package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Rand {

    /**
     * Generate random probabilities that summation is 1 for each component.
     *
     * @param size List of components
     * @param rnd  A random instance
     * @return A list of probabilities
     */
    public static List<Double> generateProbabilities(Integer size, Random rnd) {
        // 1. Define useful variables
        List<Double> probabilities = new ArrayList<>();
        double sum = 0., x = 0.;

        // 2. Generate a random number between 0 and 1 for each element in batch, and calculate sum of probabilities.
        for (int i = 0; i < size; i++) {
            x = rnd.nextDouble();
            sum += x;
            probabilities.add(x);
        }

        // 3. Divide each probability between sum, because the sum of the probabilities must be 1.
        for (int i = 0; i < size; i++) {
            probabilities.set(i, probabilities.get(i) / sum);
        }

        return probabilities;
    }
}
