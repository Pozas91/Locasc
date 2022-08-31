package models.applications;

import models.enums.ConnRange;
import models.geo.Location;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Gate {
    private List<Integer> _candidates;
    private final Integer _id;
    private Application _app;

    public Gate(List<Integer> candidates, Integer id) {
        _candidates = candidates;
        _id = id;
    }

    public Gate(Integer id) {
        _id = id;
        _candidates = new ArrayList<>();
    }

    public Gate getGate() {
        return _app.getGate(_id);
    }

    public Provider getProvider(Long threadId) {
        // Get providers index
        int iProvider = _app.getGateIProvider(threadId, _id);
        // Get selected provider by position in gates candidates list
        Integer selected = getGate().getCandidate(iProvider);
        // Get real provider selected for this gate
        return getApp().getProvider(selected);
    }

    public Provider getProvider(Integer iProvider) {
        // Get selected provider by position in services candidates list
        Integer selected = getGate().getCandidate(iProvider);
        // Get real provider selected for this service
        return getApp().getProvider(selected);
    }

    public List<Integer> getCandidates() {
        return _candidates;
    }

    public Integer getCandidate(Integer position) {
        return _candidates.get(position);
    }

    public Integer getID() {
        return _id;
    }

    public void addCandidate(Integer candidate) {
        _candidates.add(candidate);
    }

    public void addCandidates(List<Integer> candidates) {
        _candidates.addAll(candidates);
    }

    public void setCandidates(List<Integer> candidates) {
        _candidates = candidates;
    }

    public void removeCandidate(Integer provider) {
        _candidates.remove(provider);
    }

    public void setApp(Application app) {
        _app = app;
    }

    public Application getApp() {
        return _app;
    }

    public Set<Location> getLocations() {
        return getCandidates().parallelStream()
            .map(i -> getApp().getProvider(i).getLocation())
            .collect(Collectors.toSet());
    }

    public Location getLocation(Long threadID) {
        return getProvider(threadID).getLocation();
    }

    @Override
    public String toString() {
        return String.format("G%s", _id);
    }

    public Set<ConnRange> getConnRanges() {
        return getCandidates().parallelStream()
            .map(i -> getApp().getProvider(i).getConnRange())
            .collect(Collectors.toSet());
    }

    public Set<Integer> getProvidersIndex() {
        return new HashSet<>(getCandidates());
    }
}
