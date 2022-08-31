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

public class Parallel extends Architecture {
    private Location _inputGate, _outputGate;

    public Parallel(List<Component> components) {
        super(components);
    }

    public Parallel(Parallel o) {
        super(o);
        _inputGate = o._inputGate;
        _outputGate = o._outputGate;
    }

    @Override
    public Double valueN(Application app, QoS attribute, Long threadId) {
        return value(attribute, _components.parallelStream().mapToDouble(c -> c.valueN(app, attribute, threadId)));
    }

    @Override
    public Double value(Application app, QoS attribute, Long threadId) {
        return value(attribute, _components.parallelStream().mapToDouble(c -> c.value(app, attribute, threadId)));
    }

    @Override
    public Double value(Application app, QoS attribute, List<Integer> composition) {
        return value(attribute, _components.parallelStream().mapToDouble(c -> c.value(app, attribute, composition)));
    }

    @Override
    public Double value(QoS attribute, Map<Integer, Provider> composition) {
        return value(attribute, _components.parallelStream().mapToDouble(c -> c.value(attribute, composition)));
    }

    @Override
    public Double utility(UtilityApplication app, QoS k, Genotype<IntegerGene> genotype) {
        return value(k, _components.parallelStream().mapToDouble(c -> c.utility(app, k, genotype)));
    }

    @Override
    public Parallel copy() {
        return new Parallel(this);
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

    private Double value(QoS attribute, DoubleStream stream) {
        double value = switch (attribute) {
            case RESPONSE_TIME -> stream.max().orElse(0);
            case RELIABILITY, AVAILABILITY -> stream.reduce(1, (a, b) -> a * b);
            case COST -> stream.sum();
            default -> throw new IllegalStateException("Unexpected value: " + attribute);
        };

        // Apply transform function to return the value
        return attribute.getTransform().apply(value, _weight.doubleValue());
    }

    @Override
    public String toString() {
        return String.format("Parallel (%s) - %s - (%s)", _inputGate, _components, _outputGate);
    }

    @Override
    public String getName() {
        return "Parallel";
    }

}
