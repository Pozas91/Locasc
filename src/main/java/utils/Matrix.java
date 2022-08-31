package utils;

import java.util.ArrayList;
import java.util.List;

public class Matrix {
    /**
     * Calculate the transpose of the matrix (auxiliary function to flip data and save it into a CSV file correctly)
     *
     * @param table A matrix
     * @param <T>   A type of data
     * @return The transpose of the matrix given
     */
    public static <T> List<List<T>> transpose(List<List<T>> table) {
        List<List<T>> ret = new ArrayList<>();

        final int N = table.get(0).size();

        for (int i = 0; i < N; i++) {
            List<T> col = new ArrayList<>();
            for (List<T> row : table) {
                col.add(row.get(i));
            }
            ret.add(col);
        }

        return ret;
    }
}
