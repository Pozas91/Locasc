package models.auxiliary;

import java.time.Duration;

/**
 * This class works with milliseconds units!
 */
public class TimeLimit {
    // Fixed time limit to end composition process
    private Duration _timeLimit;

    // Adaptive time to composition process
    private final Boolean _adaptive;
    // Variable time that depends on number of services
    private Long _slope;
    // Fixed time (Minimum execution time to ensure that composition algorithm will works correctly)
    private Long _intercept;

    public TimeLimit(Duration timeLimit) {
        this._timeLimit = timeLimit;

        this._adaptive = false;
        this._slope = -1L;
        this._intercept = -1L;
    }

    public TimeLimit(Long slope, Long intercept) {
        this._timeLimit = null;

        this._adaptive = true;
        this._slope = slope;
        this._intercept = intercept;
    }

    public Boolean isAdaptive() {
        return _adaptive;
    }

    public Duration getDuration() {
        return _timeLimit;
    }

    public void calcAdaptiveTime(Integer nOfServices) {
        _timeLimit = Duration.ofMillis(_intercept + (_slope * nOfServices));
    }

    public Duration getApproximateLimitTime(Integer batchSize) {
        return Duration.ofMillis(_intercept + (_slope * batchSize));
    }

    public void setIntercept(Long intercept) {
        _intercept = intercept;
    }

    public void setSlope(Long slope) {
        _slope = slope;
    }

    public Long getIntercept() {
        return _intercept;
    }

    public Long getSlope() {
        return _slope;
    }
}
