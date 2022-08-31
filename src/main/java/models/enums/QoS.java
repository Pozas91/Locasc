package models.enums;

import static models.enums.ObjectiveFunction.MAXIMIZE;
import static models.enums.ObjectiveFunction.MINIMIZE;
import static models.enums.TransformFunction.IDENTITY;
import static models.enums.TransformFunction.N_SQRT;

public enum QoS {
    THROUGHPUT(MAXIMIZE, IDENTITY),
    LATENCY(MINIMIZE, IDENTITY),
    RESPONSE_TIME(MINIMIZE, IDENTITY),
    COST(MINIMIZE, IDENTITY),
    AVAILABILITY(MAXIMIZE, N_SQRT),
    //    AVAILABILITY(MAXIMIZE, IDENTITY),
    //    RELIABILITY(MAXIMIZE, IDENTITY),
    RELIABILITY(MAXIMIZE, N_SQRT);

    private final ObjectiveFunction _objective;
    private final TransformFunction _transform;

    QoS(ObjectiveFunction objective, TransformFunction transform) {
        _objective = objective;
        _transform = transform;
    }

    public ObjectiveFunction getObjective() {
        return _objective;
    }

    public TransformFunction getTransform() {
        return _transform;
    }

    @Override
    public String toString() {
        return name();
    }
}
