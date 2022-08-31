package models.patterns;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.Application;
import models.applications.Provider;
import models.applications.UtilityApplication;
import models.enums.QoS;
import models.geo.Location;

import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

/**
 * We can suppose that this architecture has always a sequential pattern inside
 */
public class Iterative extends Architecture {
    // Probability to repeat the loop
    private final Double _probability, _invProbability;
    private Location _inputGate, _outputGate;

    /**
     * Full construct
     *
     * @param components  List of components
     * @param probability Probability of repeat this loop (must have probability in (0, 1) range)
     */
    public Iterative(List<Component> components, Double probability) {
        super(components);
        _probability = probability;
        _invProbability = 1. - probability;
    }

    public Iterative(Iterative o) {
        super(o);
        _probability = o._probability;
        _invProbability = o._invProbability;
        _inputGate = o._inputGate;
        _outputGate = o._outputGate;
    }

    public Double getProbability() {
        return _probability;
    }

    public Double getInvProbability() {
        return _invProbability;
    }

    @Override
    public Double valueN(Application app, QoS attribute, Long threadId) {
        // Prepare stream for doubles
        DoubleStream values = _components.parallelStream().mapToDouble(c -> c.valueN(app, attribute, threadId));
        // Calculate the values
        return value(attribute, values);
    }

    @Override
    public Double value(Application app, QoS attribute, Long threadId) {
        // Prepare stream for doubles
        DoubleStream values = _components.parallelStream().mapToDouble(c -> c.value(app, attribute, threadId));
        // Calculate the values
        return value(attribute, values);
    }

    @Override
    public Double value(Application app, QoS attribute, List<Integer> composition) {
        // Prepare stream for doubles
        DoubleStream values = _components.parallelStream().mapToDouble(c -> c.value(app, attribute, composition));
        // Calculate the values
        return value(attribute, values);
    }

    @Override
    public Double value(QoS attribute, Map<Integer, Provider> composition) {
        // Prepare stream for doubles
        DoubleStream values = _components.parallelStream().mapToDouble(c -> c.value(attribute, composition));
        // Calculate the values
        return value(attribute, values);
    }

    @Override
    public Double utility(UtilityApplication app, QoS k, Genotype<IntegerGene> genotype) {
        // Prepare stream for doubles
        DoubleStream values = _components.parallelStream().mapToDouble(c -> c.utility(app, k, genotype));
        // Calculate the values
        return value(k, values);
    }

    @Override
    public Iterative copy() {
        return new Iterative(this);
    }

    @Override
    public void setInputGate(Location input) {
        _inputGate = input;
    }

    @Override
    public void setOutputGate(Location output) {
        _outputGate = output;
    }

    @Override
    public Location getInputGate(Application app, Long threadId) {
        return _inputGate;
    }

    @Override
    public Location getOutputGate(Application app, Long threadId) {
        return _outputGate;
    }

    private Double value(QoS attribute, DoubleStream values) {
        double value;

        switch (attribute) {
            case COST, RESPONSE_TIME -> value = values.sum() / _invProbability;
            case RELIABILITY, AVAILABILITY -> {
                double streamResult = values.reduce(1, (a, b) -> a * b);
                value = (_invProbability * streamResult) / (1 - (_probability * streamResult));
            }
            default -> throw new IllegalStateException("Unexpected value: " + attribute);
        }

        // Apply transform function to return the value
        return attribute.getTransform().apply(value, _weight.doubleValue());
    }

    @Override
    public String toString() {
        return String.format("Iterative (%s) - {%.3f - %s} - (%s)", _inputGate, _probability, _components, _outputGate);
    }

    @Override
    public String getName() {
        return "Iterative";
    }
}
