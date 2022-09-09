package problems;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.Provider;
import models.applications.UMApplication;
import models.enums.CONFIG;
import models.enums.QoS;
import utils.RunConf;
import utils.ToDebug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UMGenotype {
    private final UMApplication _app;
    private final Genotype<IntegerGene> _genotype;

    public UMGenotype(UMApplication app, Genotype<IntegerGene> genotype) {
        _app = app;
        _genotype = genotype;
    }

    public UMApplication getApp() {
        return _app;
    }

    public Genotype<IntegerGene> getGenotype() {
        return _genotype;
    }

    public static Double utility(UMGenotype appGenotype) {
        // Get application
        UMApplication app = appGenotype.getApp();
        // Get current genotype
        Genotype<IntegerGene> genotype = appGenotype.getGenotype();

        // We are used a trick to transform this genotype into a GA composition list to calculate its fitness

        // 1. Prepare number of services, gates and QoS
        int nOfQoS = app.getNOfQoS(), nOfQoSChannel = app.getChannelQoS().size(), allele, gPosition, factorQoS;
        double vNorm, v;

        // 2. Extract all services and gates to explore
        Collection<Integer> sToExplore = app.getServicesToExplore().values();
        Collection<Integer> gToExplore = app.getGatesToExplore().values();
        Collection<Integer> aToExplore = new ArrayList<>();
        aToExplore.addAll(sToExplore);
        aToExplore.addAll(gToExplore);

        // 3. Create a composition list
        Map<Integer, Provider> composition = new ConcurrentHashMap<>();

        // For each component
        for (Integer iGenotype : aToExplore) {
            // Prepare factor to multiply position in the genome
            factorQoS = (sToExplore.contains(iGenotype)) ? nOfQoS : nOfQoSChannel;

            // Prepare fake provider attributes
            Map<QoS, Double> fakeAttributes = new HashMap<>(), fakeAttributesNorm = new HashMap<>();

            for (int initialQoSIndex = 0; initialQoSIndex < factorQoS; initialQoSIndex++) {
                // Get QoS attribute
                QoS k = app.getQoSList().get(initialQoSIndex);
                // Calculate genotype position
                gPosition = (iGenotype * factorQoS) + initialQoSIndex;
                // Extract allele from genotype
                allele = genotype.get(gPosition).get(0).allele();
                // Extract values
                v = app.getQDegreeMatrix().get(iGenotype).get(k).get(allele);
                vNorm = app.getQDegreeMatrixNorm().get(iGenotype).get(k).get(allele);
                // Add value to the attribute
                fakeAttributes.put(k, v);
                fakeAttributesNorm.put(k, vNorm);
            }

            // Prepare fake provider
            Provider p = new Provider("fake", fakeAttributes);
            p.setNormalized(fakeAttributesNorm);
            composition.put(iGenotype, p);
        }

        // Prepare to extract fitness
        double fitness = (app.getSoftConstraints().isEmpty() && app.getHardConstraints().isEmpty())
            ? app.getFitnessWithoutConstraints(composition)
            : app.getFitnessWithConstraints(composition);

        if (RunConf.instance().getBoolean(CONFIG.EVOLUTION)) {
            ToDebug.getInstance().addCheckpoint(fitness, System.currentTimeMillis());
        }

        return fitness;
    }
}
