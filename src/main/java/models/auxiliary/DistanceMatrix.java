package models.auxiliary;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import generators.Locations;
import models.geo.Location;

import java.util.List;

public final class DistanceMatrix {
    private static DistanceMatrix _instance;
    private final Table<Location, Location, Double> _matrix;
    private Double _min = Double.NaN;
    private Double _max = Double.NaN;

    private DistanceMatrix() {
        _matrix = generateMatrix();
    }

    public static DistanceMatrix get() {
        if (_instance == null) {
            _instance = new DistanceMatrix();
        }

        return _instance;
    }

    public Double distance(Location a, Location b) {
        Double distance = _matrix.get(a, b);

        if (distance == null) {
            distance = _matrix.get(b, a);
        }

        return distance;
    }

    public Double min() {
        if (_min.isNaN()) {
            _min = _matrix.values().parallelStream().min(Double::compareTo).get();
        }

        return _min;
    }

    public Double max() {
        if (_max.isNaN()) {
            _max = _matrix.values().parallelStream().max(Double::compareTo).get();
        }

        return _max;
    }

    private static Table<Location, Location, Double> generateMatrix() {
        Table<Location, Location, Double> matrix = HashBasedTable.create();
        List<Location> points = Locations.get();

        for (int i = 0; i < points.size(); i++) {
            // Get point a
            Location a = points.get(i);
            // Latency between two equals points is zero, don't need to calculate it.
            matrix.put(a, a, 0.);

            for (int j = i + 1; j < points.size(); j++) {
                // Get point b
                Location b = points.get(j);
                // Introduce the distance between two points
                matrix.put(a, b, a.distance(b));
            }
        }

        return matrix;
    }
}
