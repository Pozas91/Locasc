package models.auxiliary;

import models.enums.NormalizedMethod;

import java.io.Serializable;

/**
 * Very simple class where we have two values, the min and the max value of any attribute.
 * It's used for normalize QoS attributes.
 * This class allows two types of normalization:
 * - Min-Max normalization :> (x - x_min) / (x_max - x_min)
 * - Max normalization     :> x / x_max
 */
public class Normalization implements Serializable, Cloneable {
    private Double _min;
    private Double _max;

    public Normalization(double min, double max) {
        _min = min;
        _max = max;
    }

    public Normalization(MinMax minMax) {
        _min = minMax.getMin();
        _max = minMax.getMax();
    }

    public double getMin() {
        return _min;
    }

    public void setMin(double min) {
        _min = min;
    }

    public double getMax() {
        return _max;
    }

    public void setMax(double max) {
        _max = max;
    }

    public Double normalize(Double x, Boolean toMinimize) {
        double denominator = _max - _min;

        if (denominator == 0) {
            x = 1.;
        } else {
            double numerator = (toMinimize) ? (_max - x) : (x - _min);
            x = numerator / denominator;
        }

        return x;
    }

    public Double scaling(Double x, Boolean toMinimize) {
        x = (_max == 0) ? 1. : (x / _max);

        if (toMinimize) {
            x = 1 - x;
        }

        return x;
    }

    public Double normalize(Double x, Boolean toMinimize, NormalizedMethod method) {
        return switch (method) {
            case MAX -> scaling(x, toMinimize);
            case MIN_MAX -> normalize(x, toMinimize);
        };
    }

    @Override
    public String toString() {
        return String.format("Normalization{%s, %s}", _min, _max);
    }

    @Override
    public Normalization clone() throws CloneNotSupportedException {
        return (Normalization) super.clone();
    }

    public static Double normalize(Double x, Double min, Double max, Boolean toMinimize, NormalizedMethod method) {
        Normalization n = new Normalization(min, max);
        return n.normalize(x, toMinimize, method);
    }

    public static Double normalize(Double x, Double min, Double max, Boolean toMinimize) {
        return normalize(x, min, max, toMinimize, NormalizedMethod.MIN_MAX);
    }
}
