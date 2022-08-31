package utils;

import io.jenetics.Genotype;
import io.jenetics.IntegerGene;
import models.applications.Application;
import models.applications.Gate;
import models.applications.Service;
import models.applications.UtilityApplication;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Composition {
    public static List<Integer> toList(Map<Integer, Integer> composition) {
        List<Integer> compositionList = Arrays.asList(new Integer[composition.size()]);
        composition.entrySet().parallelStream().forEach(entry -> compositionList.set(entry.getKey(), entry.getValue()));
        return compositionList;
    }

    public static List<Integer> toList(Genotype<IntegerGene> genotype, Application app) {
        List<Integer> compositionList = Arrays.asList(new Integer[genotype.length()]);
        Map<Integer, Integer> composition = Composition.toMap(genotype, app);

        for (Map.Entry<Integer, Integer> entry : composition.entrySet()) {
            compositionList.set(entry.getKey(), entry.getValue());
        }

        return compositionList;
    }

    public static List<Integer> toList(List<Service> services, Map<Service, Integer> composition) {
        return Composition.toList(Composition.toMap(services, composition));
    }

    /**
     * This function transform the composition given by the genetic algorithm into a real composition.
     * Remember that each service can have different providers, and into service all are encodes from [0, n], so we
     * need reverse this operation.
     *
     * @param genotype Combination from genetic algorithm
     * @return List of the best composition
     */
    public static Map<Integer, Integer> toMap(Genotype<IntegerGene> genotype, Application app) {
        // MARK: This function is so important, if we believe that could exist an error on compositions check this.
        Map<Integer, Integer> composition = new HashMap<>();

        // Get number of services
        int nOfServices = app.getServicesToExplore().size();

        for (Map.Entry<Integer, Integer> e : app.getServicesToExplore().entrySet()) {
            int iService = e.getKey(), iGenotypePosition = e.getValue();
            int iProviderPosition = genotype.get(iGenotypePosition).gene().allele();
            int iProvider = app.getService(iService).getCandidate(iProviderPosition);

            composition.put(iService, iProvider);
        }

        for (Map.Entry<Integer, Integer> e : app.getGatesToExplore().entrySet()) {
            int iGate = e.getKey(), iGenotypePosition = e.getValue();
            int iProviderPosition = genotype.get(iGenotypePosition).gene().allele();
            int iProvider = app.getGate(iGate).getCandidate(iProviderPosition);

            composition.put(iGate + nOfServices, iProvider);
        }

        return composition;
    }

    /**
     * Returns the composition for the application.
     *
     * @param utilities Utility at component i, the fitness that provider must have
     * @return The application solver with its composition
     */
    public static Map<Integer, Integer> toUtility(Map<Integer, Double> utilities, UtilityApplication uApp) {
        // MARK: This function is so important, if we believe that could exist an error on compositions check this.
        Map<Integer, Integer> composition = new HashMap<>();

        for (Map.Entry<Integer, Integer> e : uApp.getServicesToExplore().entrySet()) {
            Integer iService = e.getKey(), iGenotype = e.getValue();
            Service s = uApp.getService(iService);

            _toUtility(utilities, uApp, composition, iGenotype, s.getCandidates());
        }

        for (Map.Entry<Integer, Integer> e : uApp.getGatesToExplore().entrySet()) {
            Integer iGate = e.getKey(), iGenotype = e.getValue();
            Gate g = uApp.getGate(iGate);

            _toUtility(utilities, uApp, composition, iGenotype, g.getCandidates());
        }

        return composition;
    }

    private static void _toUtility(
        Map<Integer, Double> utilities, UtilityApplication uApp, Map<Integer, Integer> composition, Integer iGenotype,
        List<Integer> candidates
    ) {
        double bestApprox = Double.MAX_VALUE;
        int bestProvider = -1;

        for (Integer p : candidates) {
            double utility = uApp.getUtilityProvider().get(iGenotype).get(p);
            double approx = Math.abs(utilities.get(iGenotype) - utility);

            // New, this is if we want to approximate to the fitness required...
            if (approx < bestApprox) {
                bestProvider = p;
                bestApprox = approx;
            }
        }

        composition.put(iGenotype, bestProvider);
    }

    public static Map<Integer, Integer> toMap(List<Service> services, Map<Service, Integer> composition) {
        Map<Integer, Integer> solution = new HashMap<>();

        for (Map.Entry<Service, Integer> entry : composition.entrySet()) {
            solution.put(services.indexOf(entry.getKey()), entry.getValue());
        }

        return solution;
    }

    public static Map<Integer, Integer> toMap(List<Integer> genotype) {
        Map<Integer, Integer> solution = new HashMap<>();

        for (int i = 0; i < genotype.size(); i++) {
            solution.put(i, genotype.get(i));
        }

        return solution;
    }
}
