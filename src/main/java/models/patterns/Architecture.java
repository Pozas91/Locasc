package models.patterns;

import models.applications.Application;
import models.applications.Service;
import org.javatuples.Quartet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Architecture extends Component {
    protected final List<Component> _components;
    protected final Set<Component> _internalLinks;
    protected Integer _inGateID;
    protected Integer _outGateID;

    public Architecture(List<Component> components) {
        super();
        _components = components;
        _weight = components.parallelStream().mapToInt(Component::weight).sum();
        _internalLinks = new HashSet<>();
        _inGateID = -1;
        _outGateID = -1;
    }

    /**
     * For this class we need a deep copy constructor
     *
     * @param o An architecture to copy
     */
    public Architecture(Architecture o) {
        super(o);

        _internalLinks = new HashSet<>(o._internalLinks);

        _inGateID = o._inGateID;
        _outGateID = o._outGateID;

        // MARK: Need deep copy
        _components = o._components.stream().map(Component::copy).collect(Collectors.toList());
    }

    public List<Component> getComponents() {
        return _components;
    }

    public Component getComponent(Integer index) {
        return _components.get(index);
    }

    public abstract String getName();

    /**
     * Get all services for this architecture
     *
     * @return A list of services
     */
    public List<Service> getServices(Application app) {
        return _components
            .parallelStream()
            .map(c -> c.getServices(app))
            .flatMap(Collection::parallelStream)
            .collect(Collectors.toList());
    }

    /**
     * Get all services for this architecture
     *
     * @return A list of base components
     */
    public List<BaseComponent> getBaseComponents() {
        return _components
            .parallelStream()
            .map(Component::getBaseComponents)
            .flatMap(Collection::parallelStream)
            .collect(Collectors.toList());
    }

    public List<IndexService> getIndexServices() {
        return _components
            .parallelStream()
            .map(Component::getIndexServices)
            .flatMap(Collection::parallelStream)
            .collect(Collectors.toList());
    }

    /**
     * Function that returns the times that each pattern appears into this architecture
     *
     * @return (nOfIterative, nOfConditionals, nOfParallels, nOfSequential)
     */
    public Quartet<Integer, Integer, Integer, Integer> countPatterns() {
        // Define counters
        Quartet<Integer, Integer, Integer, Integer> countersBase = new Quartet<>(
            this instanceof Iterative ? 1 : 0,
            this instanceof Conditional ? 1 : 0,
            this instanceof Parallel ? 1 : 0,
            this instanceof Sequential ? 1 : 0
        );

        // Count components counters
        Quartet<Integer, Integer, Integer, Integer> countersNew;

        // Get all components for this level
        List<Component> components = getComponents();

        for (Component c : components) {
            if (c instanceof Architecture) {
                // Recursive call
                countersNew = ((Architecture) c).countPatterns();

                // Counters base updating
                countersBase = countersBase.setAt0(countersBase.getValue0() + countersNew.getValue0());
                countersBase = countersBase.setAt1(countersBase.getValue1() + countersNew.getValue1());
                countersBase = countersBase.setAt2(countersBase.getValue2() + countersNew.getValue2());
                countersBase = countersBase.setAt3(countersBase.getValue3() + countersNew.getValue3());
            }
        }

        return countersBase;
    }

    public Set<Component> getLinks() {
        return _internalLinks;
    }

    public void addLink(Component internal) {
        _internalLinks.add(internal);
    }

    public void setInGateID(Integer id) {
        _inGateID = id;
    }

    public void setOutGateID(Integer id) {
        _outGateID = id;
    }

    public Integer getInGateID() {
        return _inGateID;
    }

    public Integer getOutGateID() {
        return _outGateID;
    }

    public void incrementWeight(Integer increment) {
        _weight += increment;
    }

    @Override
    public Architecture getArchitecture() {
        return this;
    }

    @Override
    public String toString() {
        return String.format("Architecture{components: %s, size: %s}", _components, weight());
    }

    @Override
    public Boolean isBase() {
        return false;
    }

    @Override
    public int compareTo(@NotNull Component o) {
        return _weight.compareTo(o._weight);
    }
}
