package models.auxiliary;

public class Range<N extends Number> {
    private final N _from;
    private final N _to;

    public Range(N from, N to) {
        _from = from;
        _to = to;
    }

    public Range(N value) {
        _from = _to = value;
    }

    public N from() {
        return _from;
    }

    public N to() {
        return _to;
    }

    public Boolean hasRange() {
        return _from.doubleValue() < _to.doubleValue();
    }
}
