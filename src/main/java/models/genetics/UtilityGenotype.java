package models.genetics;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.UtilityApplication;
import models.auxiliary.Constraint;
import models.enums.CONFIG;
import models.enums.QoS;
import utils.RunConf;
import utils.ToDebug;

import java.util.*;

public class UtilityGenotype {
    private final UtilityApplication _app;
    private final Genotype<IntegerGene> _genotype;

    public UtilityGenotype(UtilityApplication app, Genotype<IntegerGene> genotype) {
        _app = app;
        _genotype = genotype;
    }

    public UtilityApplication getApp() {
        return _app;
    }

    public Genotype<IntegerGene> getGenotype() {
        return _genotype;
    }

    public static Double fitness(UtilityGenotype appGenotype) {
        List<Double> valuesOfQoS, listToConst;
        double Q, penalty = 0., fitness = 0.;

        // Get application
        UtilityApplication app = appGenotype.getApp();
        // Get current genotype
        Genotype<IntegerGene> genotype = appGenotype.getGenotype();
        // Get fitness's normalized matrix
        Map<Integer, Map<QoS, List<Double>>> utilityMatrix = app.getUtilityNormalizedMatrix();
        // Map to save genome's fitness
        Map<QoS, Double> utilityQoSGenome = new LinkedHashMap<>();

        // Prepare number of services, gates and QoS
        int nOfQoS = app.getNOfQoS(), nOfQoSChannel = app.getChannelQoS().size(), allele, gPosition, factorQoS;

        // Extract all services and gates to explore
        Collection<Integer> sToExplore = app.getServicesToExplore().values();
        Collection<Integer> gToExplore = app.getGatesToExplore().values();
        Collection<Integer> aToExplore = new ArrayList<>();
        aToExplore.addAll(sToExplore);
        aToExplore.addAll(gToExplore);

        // For each QoS attribute
        for (int initialQoSIndex = 0; initialQoSIndex < nOfQoS; initialQoSIndex++) {
            // Get QoS attribute
            QoS k = app.getQoSList().get(initialQoSIndex);
            // Prepare partial summation for fitness and for constraint
            Q = 0.;
            listToConst = new ArrayList<>();

            // Prepare selected collection to iterate
            Collection<Integer> selectedCollection = (initialQoSIndex < nOfQoSChannel) ? aToExplore : sToExplore;

            // For each component
            for (Integer iGenotype : selectedCollection) {
                // Prepare factor to multiply position in the genome
                factorQoS = (sToExplore.contains(iGenotype)) ? nOfQoS : nOfQoSChannel;
                // Extract list of values
                valuesOfQoS = utilityMatrix.get(iGenotype).get(k);
                // Calculate genotype position
                gPosition = (iGenotype * factorQoS) + initialQoSIndex;
                // Extract allele from genotype
                allele = genotype.get(gPosition).get(0).allele();
                // Add to partial summation
                Q += valuesOfQoS.get(allele);
                // Add normalize quality degree for this allele to calculate later the constraint
                listToConst.add(app.getQDegreeMatrixNorm().get(iGenotype).get(k).get(allele));
            }

            // Calculate penalty
            if (app.getNormalizedConstraints().containsKey(k)) {
                // Get normalized aggregated-quality
                double refQuality = switch (k) {
                    case THROUGHPUT -> listToConst.parallelStream().mapToDouble(d -> d).min().getAsDouble();
                    case COST, RESPONSE_TIME, LATENCY ->
                        listToConst.parallelStream().mapToDouble(d -> d).average().getAsDouble();
                    case AVAILABILITY, RELIABILITY -> listToConst.parallelStream().mapToDouble(d -> d)
                        .reduce(1., (a, b) -> Math.sqrt(a * b));
                };

                // Get value of the constraint
                Constraint constraint = app.getNormalizedConstraints().get(k);

                if (constraint.isInvalid(refQuality)) {
                    penalty += app.getWeights().get(k);
                }
            }

            // Save the fitness for current genome
            utilityQoSGenome.put(k, Q / selectedCollection.size());
        }

        // Multiply all QoS attributes per it weight
        for (QoS qos : app.getQoSList()) {
            fitness += app.getWeights().get(qos) * utilityQoSGenome.get(qos);
        }

        // Apply penalization
        fitness -= penalty;

        // Save fitness if is necessary
        if (RunConf.instance().getBoolean(CONFIG.EVOLUTION)) {
            ToDebug.getInstance().addCheckpoint(fitness, System.currentTimeMillis());
        }

        return fitness;
    }
}
