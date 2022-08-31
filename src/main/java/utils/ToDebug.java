package utils;

import java.util.ArrayList;
import java.util.List;

/*
TODO: This class is only for debug purposes, please don't remove.
 */
public final class ToDebug {
    private static ToDebug instance;
    private static Double _MAX_FITNESS = Double.MIN_VALUE, _MIN_FITNESS = Double.MAX_VALUE, _LAST_FITNESS;
    private static List<Integer> _MAX_COMPOSITION, _MIN_COMPOSITION;
    private static final List<Double> _FITNESS = new ArrayList<>();
    private static final List<Long> _TIME = new ArrayList<>();

    public static ToDebug getInstance() {
        if (instance == null) {
            instance = new ToDebug();
        }

        return instance;
    }

    public void addCheckpoint(Double fitness, Long timestamp) {

        if (fitness > _MAX_FITNESS) {
            _MAX_FITNESS = fitness;
        }

        _FITNESS.add(fitness);
        _TIME.add(timestamp);
    }

    public void newFitness(Double fitness, List<Integer> composition) {
        if (fitness > _MAX_FITNESS) {
            _MAX_FITNESS = fitness;
            _MAX_COMPOSITION = composition;
        }

        if (fitness < _MIN_FITNESS) {
            _MIN_FITNESS = fitness;
            _MIN_COMPOSITION = composition;
        }

        _LAST_FITNESS = fitness;
        _FITNESS.add(fitness);
    }

    public Double getMaxFitness() {
        return _MAX_FITNESS;
    }

    public Double getMinFitness() {
        return _MIN_FITNESS;
    }

    public Double getLastFitness() {
        return _LAST_FITNESS;
    }

    public List<Integer> getMaxComposition() {
        return _MAX_COMPOSITION;
    }

    public List<Integer> getMinComposition() {
        return _MIN_COMPOSITION;
    }

    public List<Double> getFitness() {
        return _FITNESS;
    }

    public List<Long> getTime() {
        return _TIME;
    }

    public void clear() {
        _FITNESS.clear();
        _TIME.clear();
        _LAST_FITNESS = null;
        _MAX_FITNESS = Double.MIN_VALUE;
        _MIN_FITNESS = Double.MAX_VALUE;
    }
}
