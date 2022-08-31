package models.enums;

public enum TransformFunction {
    IDENTITY, LOG_10, SIGMOID, N_SQRT;

    public Double apply(Double x, Double power) {
        return switch (this) {
            case IDENTITY -> x;
            case LOG_10 -> Math.log10(x);
            case N_SQRT -> Math.pow(x, 1 / power);
            case SIGMOID -> (1 / (1 + Math.exp(-x)));
        };
    }

    @Override
    public String toString() {
        return name();
    }
}
