package models.auxiliary;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A simple min-max class for doubles values, given some values keep in this class the maximum and the minimum of these
 * values.
 */
public class MinMax {
    private Double _min;
    private Double _max;

    public MinMax() {
        _min = Double.MAX_VALUE;
        _max = Double.MIN_VALUE;
    }

    public MinMax(Double... values) {
        this(Arrays.asList(values));
    }

    public MinMax(double[] values) {
        this(ArrayUtils.toObject(values));
    }

    public MinMax(List<Double> values) {
        _min = Collections.min(values);
        _max = Collections.max(values);
    }

    public void setMin(Double min) {
        _min = Math.min(min, _min);
    }

    public Double getMin() {
        return _min;
    }

    public void setMax(Double max) {
        _max = Math.max(max, _max);
    }

    public Double getMax() {
        return _max;
    }

    public void setMinMax(Double value) {
        _min = Math.min(value, _min);
        _max = Math.max(value, _max);
    }

    @Override
    public String toString() {
        return String.format("MinMax{%s, %s}", _min, _max);
    }
}
