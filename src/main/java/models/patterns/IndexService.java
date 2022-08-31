package models.patterns;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.Application;
import models.applications.Provider;
import models.applications.UtilityApplication;
import models.enums.ConnRange;
import models.enums.QoS;
import models.geo.Location;

import java.util.*;
import java.util.stream.Collectors;

public class IndexService extends BaseComponent {
    /**
     * By default, a zero provider is selected
     */
    public IndexService(Integer id) {
        super();
        _id = id;
        _weight = 1;
    }

    public IndexService(IndexService o) {
        super(o);
        _weight = 1;
    }

    public Provider getProvider(Application app, Long threadId) {
        // Get provider index
        int iProvider = app.getServiceIProvider(threadId, _id);
        // Get selected provider by position in services candidates list
        Integer selected = getService(app).getCandidate(iProvider);
        // Get real provider selected for this service
        return app.getProvider(selected);
    }

    public Provider getProvider(Application app, Integer iProvider) {
        // Get selected provider by position in services candidates list
        Integer selected = getService(app).getCandidate(iProvider);
        // Get real provider selected for this service
        return app.getProvider(selected);
    }

    @Override
    public Double valueN(Application app, QoS attribute, Long threadId) {
        return getProvider(app, threadId).getNormalizedValue(attribute);
    }

    @Override
    public Double value(Application app, QoS attribute, Long threadId) {
        return getProvider(app, threadId).getAttributeValue(attribute);
    }

    @Override
    public Double value(Application app, QoS attribute, List<Integer> composition) {
        return getProvider(app, composition.get(_id)).getAttributeValue(attribute);
    }

    @Override
    public Double value(QoS attribute, Map<Integer, Provider> composition) {
        return composition.get(_id).getAttributeValue(attribute);
    }

    @Override
    public Double utility(UtilityApplication app, QoS k, Genotype<IntegerGene> genotype) {
        // Extract basic references
        int factorQoS = app.getNOfQoS(), iGenotype = app.getServiceToExplore(_id), kIndex = app.getQoSList().indexOf(k);
        // Calculate the position of genome to observe
        int gPosition = (iGenotype * factorQoS) + kIndex;
        // Extract allele of this component
        int allele = genotype.get(gPosition).get(0).allele();
        // Return correct utility's value
        return app.getUtilityNormalizedMatrix().get(iGenotype).get(k).get(allele);
    }

    @Override
    public IndexService copy() {
        return new IndexService(this);
    }

    @Override
    public List<IndexService> getIndexServices() {
        return Collections.singletonList(this);
    }

    public Set<Location> getLocations(Application app) {
        return getService(app).getCandidates().parallelStream()
            .map(i -> app.getProvider(i).getLocation())
            .collect(Collectors.toSet());
    }

    public Set<ConnRange> getConnRanges(Application app) {
        return getService(app).getCandidates().parallelStream()
            .map(i -> app.getProvider(i).getConnRange())
            .collect(Collectors.toSet());
    }

    @Override
    public void setInputGate(Location input) {
    }

    @Override
    public void setOutputGate(Location output) {
    }

    @Override
    public Location getInputGate(Application app, Long threadId) {
        return getProvider(app, threadId).getLocation();
    }

    @Override
    public Location getOutputGate(Application app, Long threadId) {
        return getProvider(app, threadId).getLocation();
    }

    // MARK: Static
    public static IndexService i(Integer i) {
        return new IndexService(i);
    }

    public Set<Integer> getProvidersIndex(Application app) {
        return new HashSet<>(getService(app).getCandidates());
    }
}
