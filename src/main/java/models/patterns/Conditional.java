package models.patterns;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.Application;
import models.applications.Provider;
import models.applications.UtilityApplication;
import models.enums.QoS;
import models.geo.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class Conditional extends Architecture {
    private final List<Double> _probabilities;
    private Location _inputGate, _outputGate;

    /**
     * Construct full parameters
     *
     * @param components List of components with a probability
     */
    public Conditional(List<Component> components, List<Double> probabilities) {
        super(components);
        _probabilities = new ArrayList<>(probabilities);
    }

    /**
     * We need a deep copy constructor for this class
     *
     * @param o A Conditional pattern to copy
     */
    public Conditional(Conditional o) {
        super(o);
        _probabilities = new ArrayList<>(o._probabilities);
        _inputGate = o._inputGate;
        _outputGate = o._outputGate;
    }

    public List<Double> getProbabilities() {
        return _probabilities;
    }

    public Double getProbability(int index) {
        return _probabilities.get(index);
    }

    @Override
    public Double valueN(Application app, QoS attribute, Long threadId) {
        return IntStream.range(0, _components.size()).parallel()
            .mapToDouble(i -> _components.get(i).valueN(app, attribute, threadId) * _probabilities.get(i))
            .sum();
    }

    @Override
    public Double value(Application app, QoS attribute, Long threadId) {
        return IntStream.range(0, _components.size()).parallel()
            .mapToDouble(i -> _components.get(i).value(app, attribute, threadId) * _probabilities.get(i))
            .sum();
    }

    @Override
    public Double value(Application app, QoS attribute, List<Integer> composition) {
        return IntStream.range(0, _components.size()).parallel()
            .mapToDouble(i -> _components.get(i).value(app, attribute, composition) * _probabilities.get(i))
            .sum();
    }

    @Override
    public Double value(QoS attribute, Map<Integer, Provider> composition) {
        return IntStream.range(0, _components.size()).parallel()
            .mapToDouble(i -> _components.get(i).value(attribute, composition) * _probabilities.get(i))
            .sum();
    }

    @Override
    public Double utility(UtilityApplication app, QoS k, Genotype<IntegerGene> genotype) {
        return IntStream.range(0, _components.size()).parallel()
            .mapToDouble(i -> _components.get(i).utility(app, k, genotype) * _probabilities.get(i))
            .sum();
    }

    @Override
    public Conditional copy() {
        return new Conditional(this);
    }

    @Override
    public String toString() {
        return String.format("Conditional (%s) - {%s} - (%s)", _inputGate, _components, _outputGate);
    }

    @Override
    public String getName() {
        return "Conditional";
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
}
