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

public class Sequential extends Architecture {
    public Sequential(List<Component> components) {
        super(components);
    }

    public Sequential(Sequential o) {
        super(o);
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

    private Double value(QoS attribute, DoubleStream stream) {
        double value = switch (attribute) {
            case RELIABILITY, AVAILABILITY -> stream.reduce(1, (a, b) -> a * b);
            case COST, RESPONSE_TIME -> stream.sum();
            default -> throw new IllegalStateException("Unexpected value: " + attribute);
        };

        // Apply transform function to return the value
        return attribute.getTransform().apply(value, _weight.doubleValue());
    }

    @Override
    public Component copy() {
        return new Sequential(this);
    }

    @Override
    public void setInputGate(Location input) {
    }

    @Override
    public void setOutputGate(Location output) {
    }

    @Override
    public Location getInputGate(Application app, Long threadId) {
        return _components.get(0).getInputGate(app, threadId);
    }

    @Override
    public Location getOutputGate(Application app, Long threadId) {
        return _components.get(_components.size() - 1).getOutputGate(app, threadId);
    }

    @Override
    public String toString() {
        return String.format("Sequential {%s}", _components);
    }

    @Override
    public String getName() {
        return "Sequential";
    }

    @Override
    public void setInGateID(Integer id) {
        // Avoid set an id for gate
    }

    @Override
    public void setOutGateID(Integer id) {
        // Avoid set an id for gate
    }
}
