package models.auxiliary;

import models.applications.Application;
import models.applications.Gate;
import models.applications.Provider;
import models.geo.Location;
import models.patterns.Component;
import models.patterns.IndexService;
import utils.Sets;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// This class can help to offer faster way to work with latency and throughput
public class Node {
    private Component _component;
    private Location _location;
    private Boolean _parallels;
    private Integer _gateId;
    private final List<Node> _next;
    private final List<Double> _factors;
    private final Application _app;

    public Node(Application app, Integer gateId) {
        _next = new ArrayList<>();
        _parallels = false;
        _component = null;
        _location = null;
        _factors = new ArrayList<>();
        _app = app;
        _gateId = gateId;
    }

    public Node(Application app) {
        this(app, -1);
    }

    public Component getComponent() {
        return _component;
    }

    public void setComponent(Component component) {
        this._component = component;
    }

    public Location getLocation() {
        return _location;
    }

    public Location getLocation(Application app, Long threadID) {
        if (_location != null) {
            return _location;
        } else if (_component != null) {
            return getComponent().getInputGate(app, threadID);
        } else if (_gateId >= 0) {
            return getGate().getLocation(threadID);
        } else {
            throw new RuntimeException("Something go wrong getting location");
        }
    }

    public Location getLocation(List<Integer> composition) {
        if (_location != null) {
            return _location;
        } else if (_component != null) {
            Integer iService = ((IndexService) getComponent()).getIService();
            Integer iGenotype = _app.getServicesToExplore().get(iService);

            if (composition.size() > iGenotype) {
                return _app.getProvider(composition.get(iGenotype)).getLocation();
            } else {
//                System.err.printf("Position doesn't found, autocompleting gates? %d%n", iGenotype);
                return _app.getProvider(0).getLocation();
            }
        } else if (_gateId >= 0) {
            Integer iGate = getGateID();
            Integer iGenotype = _app.getGatesToExplore().get(iGate);

            if (composition.size() > iGenotype) {
                return _app.getProvider(composition.get(iGenotype)).getLocation();
            } else {
//                System.err.printf("Position doesn't found, autocompleting gates? %d%n", iGenotype);
                return _app.getProvider(0).getLocation();
            }
        } else {
            throw new RuntimeException("Something go wrong getting location");
        }
    }

    public Provider getProvider(Long threadId) {
        if (_location != null) {
            return null;
        } else if (_component != null) {
            return ((IndexService) getComponent()).getProvider(_app, threadId);
        } else if (_gateId >= 0) {
            return getGate().getProvider(threadId);
        } else {
            throw new RuntimeException("Something go wrong getting provider");
        }
    }

    public Provider getProvider(List<Integer> composition) {
        if (_location != null) {
            return null;
        } else if (_component != null) {
            Integer iService = ((IndexService) getComponent()).getIService();
            Integer iGenotype = _app.getServicesToExplore().get(iService);

            if (composition.size() > iGenotype) {
                return _app.getProvider(composition.get(iGenotype));
            } else {
                return _app.getProvider(0);
            }
        } else if (_gateId >= 0) {
            Integer iGate = getGateID();
            Integer iGenotype = _app.getGatesToExplore().get(iGate);

            if (composition.size() > iGenotype) {
                return _app.getProvider(composition.get(iGenotype));
            } else {
//                System.err.printf("Position doesn't found, autocompleting gates? %d%n", iGenotype);
                return _app.getProvider(0);
            }
        } else {
            throw new RuntimeException("Something go wrong getting location");
        }
    }

    private Gate getGate() {
        return getApp().getGate(_gateId);
    }

    public void setLocation(Location location) {
        this._location = location;
    }

    public Set<Location> getLocations() {
        if (_location != null) {
            return Sets.of(_location);
        } else if (_gateId >= 0) {
            return getGate().getLocations();
        } else if (_component != null) {
            return ((IndexService) getComponent()).getLocations(_app);
        } else {
            throw new RuntimeException("Something go wrong getting locations");
        }
    }

    public Boolean getParallels() {
        return _parallels;
    }

    public void setParallels(Boolean parallels) {
        this._parallels = parallels;
    }

    public Integer getGateID() {
        return _gateId;
    }

    public void setGateID(Integer gateID) {
        this._gateId = gateID;
    }

    public List<Node> getNext() {
        return _next;
    }

    public List<Double> getFactors() {
        return _factors;
    }

    public Application getApp() {
        return _app;
    }

    public void add(Node next, Double factor) {
        _next.add(next);
        _factors.add(factor);
    }

    public Node getNext(int i) {
        return _next.get(i);
    }

    public void setFactor(int i, Double factor) {
        _factors.set(i, factor);
    }

    public Double getFactor(int i) {
        return _factors.get(i);
    }

    public Set<Integer> getProvidersIndex(Application app) {
        if (_location != null) {
            return Sets.of();
        } else if (_component != null) {
            return ((IndexService) getComponent()).getProvidersIndex(app);
        } else if (_gateId >= 0) {
            return getGate().getProvidersIndex();
        } else {
            throw new RuntimeException("Something go wrong getting provider");
        }
    }
}
