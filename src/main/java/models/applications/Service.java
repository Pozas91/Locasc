package models.applications;

import java.util.List;

// This class is called `Service` in papers that we've read.
public class Service {
    // Define app instance (OneToMany relationship)
    private final String _name;

    /*
    This is important to understand, this is a list of indexes that correspond with list of providers in global app,
    each service will have any amount of these providers. p.e. These services would be available the providers [2, 3, 5]
    0 -> 2, first position
    1 -> 3, second position
    2 -> 5, third position
    */
    private final List<Integer> _candidates;

    public Service(String name, List<Integer> candidates) {
        _name = name;
        _candidates = candidates;
    }

    public Service(Service o) {
        _name = o._name;
        _candidates = o._candidates;
    }

    public Service copy() {
        return new Service(this);
    }

    public String getName() {
        return _name;
    }

    public List<Integer> getCandidates() {
        return _candidates;
    }

    /**
     * Return the candidate in that position
     *
     * @param position A position to find the candidate
     */
    public Integer getCandidate(Integer position) {
        return _candidates.get(position);
    }

    @Override
    public String toString() {
        return _name;
    }

    public void addCandidate(Integer lastProvider) {
        _candidates.add(lastProvider);
    }

    public void removeCandidate(Integer candidate) {
        _candidates.remove(candidate);
    }
}
