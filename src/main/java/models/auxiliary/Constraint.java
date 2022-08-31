package models.auxiliary;

import models.enums.ConstraintOperator;
import models.enums.ConstraintType;

public class Constraint {
    private final ConstraintOperator _operator;
    private final Double _ref, _refTop;
    private final ConstraintType _type;

    public Constraint(ConstraintOperator operator, Double ref) {
        this(operator, ref, null, ConstraintType.LITERAL);
    }

    public Constraint(ConstraintOperator operator, Double ref, Double refTop) {
        this(operator, ref, refTop, ConstraintType.LITERAL);
    }

    public Constraint(ConstraintOperator operator, Double ref, Double refTop, ConstraintType type) {
        _operator = operator;
        _ref = ref;
        _refTop = refTop;
        _type = type;
    }

    public ConstraintOperator getOperator() {
        return _operator;
    }

    public Double getRef() {
        return _ref;
    }

    public Double getRefTop() {
        return _refTop;
    }

    public Boolean isValid(Double value) {
        if (_operator.equals(ConstraintOperator.IN_RANGE) && _refTop == null) {
            throw new RuntimeException("IN_RANGE operator needs _ref and _refTop params, please check it.");
        }

        return switch (_operator) {
            case LESS_THAN -> value < _ref;
            case GREATER_THAN -> value > _ref;
            case IN_RANGE -> _ref < value && value < _refTop;
        };
    }

    public Boolean isValidPercentage(Double value, MinMax minMax) {
        if (_operator.equals(ConstraintOperator.IN_RANGE) && _refTop == null) {
            throw new RuntimeException("IN_RANGE operator needs _ref and _refTop params, please check it.");
        }

        return switch (_operator) {
            case LESS_THAN -> value < minMax.getMin() * _ref;
            case GREATER_THAN -> value > minMax.getMin() * _ref;
            case IN_RANGE -> minMax.getMin() * _ref < value && value < (minMax.getMax() * _refTop);
        };
    }

    public Boolean isInvalid(Double value) {
        return !isValid(value);
    }

    public Boolean isInvalidPercentage(Double value, MinMax minMax) {
        return !isValidPercentage(value, minMax);
    }

    @Override
    public String toString() {
        return switch (_operator) {
            case LESS_THAN -> String.format("x < %s", _ref);
            case GREATER_THAN -> String.format("x > %s", _ref);
            case IN_RANGE -> String.format("%s < x < %s", _ref, _refTop);
        };
    }
}
