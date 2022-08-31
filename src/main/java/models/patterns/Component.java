package models.patterns;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;
import models.applications.UtilityApplication;
import models.enums.QoS;
import models.geo.Location;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class Component implements Comparable<Component> {
    protected Integer _weight;
    protected Set<Component> _nextComponents;
    protected Component _father;

    public Component() {
        _nextComponents = new HashSet<>();
        _father = null;
    }

    public Component(Component o) {
        _weight = o._weight;
        _nextComponents = new HashSet<>(o._nextComponents);
        _father = o._father;
    }

    /**
     * Return the value normalized of this component
     *
     * @param attribute QoS attribute to get the value
     * @return Value normalized for these attribute and component
     */
    public abstract Double valueN(Application app, QoS attribute, Long threadId);

    /**
     * Return the original value of this component
     *
     * @param attribute QoS attribute to get the value
     * @return Original value for these attribute and component
     */
    public abstract Double value(Application app, QoS attribute, Long threadId);

    public abstract Double value(Application app, QoS attribute, List<Integer> composition);

    public abstract Double value(QoS attribute, Map<Integer, Provider> composition);

    public abstract Double utility(UtilityApplication app, QoS k, Genotype<IntegerGene> genotype);

    /**
     * Return the size of this component
     *
     * @return An integer that represents the size of this component (service are 1)
     */
    public Integer weight() {
        return _weight;
    }

    /**
     * Return if the component is base or not, by default not
     *
     * @return True if is false, False otherwise.
     */
    public abstract Boolean isBase();

    /**
     * Create a deep copy for the component given
     *
     * @return A copy of the component
     */
    public abstract Component copy();

    /**
     * Return the architecture for this component:
     * - if this component is an architecture, then return it.
     * - if this component is a component, then return a Sequential single-component.
     *
     * @return An architecture for this component
     */
    public abstract Architecture getArchitecture();

    /**
     * Return the list of the services for this component:
     * - if is a base component, return a list with a single service.
     * - if is an architecture, return the services for that architecture.
     *
     * @return A list of the services
     */
    public abstract List<Service> getServices(Application app);

    /**
     * Return the list of the base components for this component:
     * - if is a base component, return a list with a single base component.
     * - if is an architecture, return the base components for that architecture.
     *
     * @return A list of the base components
     */
    public abstract List<BaseComponent> getBaseComponents();

    /**
     * Return all IndexesServices in this component
     * - If is a IndexService, return a singleton list.
     * - If is a Placeholder, return an empty list.
     * - If is an Architecture, call recursive function
     *
     * @return A list of index services
     */
    public abstract List<IndexService> getIndexServices();

    public abstract void setInputGate(Location input);

    public abstract void setOutputGate(Location output);

    public abstract Location getInputGate(Application app, Long threadId);

    public abstract Location getOutputGate(Application app, Long threadId);

    public void addNext(Component next) {
        _nextComponents.add(next);
    }

    public Set<Component> getNextComponents() {
        return _nextComponents;
    }

    public void clearNext() {
        _nextComponents.clear();
    }

    public void addFather(Component father) {
        _father = father;
    }

    public Component getFather() {
        return _father;
    }

    @Override
    public int compareTo(@NotNull Component o) {
        return _weight.compareTo(o._weight);
    }
}
