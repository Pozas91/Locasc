package problems;

import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.engine.Codec;
import io.jenetics.util.ISeq;
import io.jenetics.util.IntRange;
import models.applications.Application;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class ApplicationProblem extends GeneralProblem<Application> {

    public ApplicationProblem(final Application app) {
        Genotype<IntegerGene> genotype = Genotype.of(getChromosomes(app));
        _codec = Codec.of(genotype, app::copy);
    }

    @Override
    public Double fitness(Application arg) {
        return arg.fitness();
    }

    /**
     * Prepare a list of chromosomes (cannot be gene because in a chromosome all genes must be same range).
     *
     * @param app An application instance
     * @return A list of chromosomes
     */
    public static ISeq<IntegerChromosome> getChromosomes(Application app) {
        // Get numbers of services and gates
        int nOfServices = app.getServicesToExplore().size();
        int nOfGates = app.getGatesToExplore().size();

        // Define list of chromosomes
        List<IntegerChromosome> chromosomes = Arrays.asList(new IntegerChromosome[nOfServices + nOfGates]);

        // Get services to explore
        app.getServicesToExplore().entrySet().parallelStream()
            // Map each service as IntegerChromosome with only a IntegerGene, from 0 to candidates list's size (less 1).
            .forEach(e -> {
                // Define the chromosome
                IntegerChromosome chromosome = IntegerChromosome.of(
                    IntRange.of(0, app.getService(e.getKey()).getCandidates().size() - 1)
                );
                // Add chromosomes for this
                chromosomes.set(e.getValue(), chromosome);
            });

        // Get gates to explore
        app.getGatesToExplore().entrySet().parallelStream()
            .forEach(e -> {
                IntegerChromosome chromosome = IntegerChromosome.of(
                    IntRange.of(0, app.getGate(e.getKey()).getCandidates().size() - 1)
                );
                // Add chromosomes for this
                chromosomes.set(e.getValue(), chromosome);
            });

        // Return the chromosomes
        return ISeq.of(chromosomes);
    }
}
