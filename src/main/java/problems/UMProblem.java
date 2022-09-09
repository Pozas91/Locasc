package problems;

import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.engine.Codec;
import io.jenetics.util.ISeq;
import io.jenetics.util.IntRange;
import models.applications.UMApplication;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public final class UMProblem extends GeneralProblem<UMGenotype> {

    public UMProblem(final UMApplication app) {
        _codec = Codec.of(Genotype.of(getChromosomes(app)), gt -> new UMGenotype(app, gt));
    }

    @Override
    public Double fitness(UMGenotype arg) {
        return UMGenotype.utility(arg);
    }

    /**
     * Prepare a list of chromosomes (cannot be gene because in a chromosome all genes must be same range).
     *
     * @param app An application instance
     * @return A list of chromosomes
     */
    public static ISeq<IntegerChromosome> getChromosomes(UMApplication app) {
        // Extract number of the services
        int
            nOfServices = app.getServicesToExplore().size(),
            nOfGates = app.getGatesToExplore().size(),
            nOfDegrees = app.getNOfDegrees();

        // Size of chromosomes
        int totalSize = app.getChannelQoS().size() * nOfGates + app.getQoSSize() * nOfServices;

        List<IntegerChromosome> chromosomes = IntStream.range(0, totalSize)
            .mapToObj(i -> IntegerChromosome.of(IntRange.of(0, nOfDegrees - 1)))
            .collect(Collectors.toList());

        // Return the chromosomes
        return ISeq.of(chromosomes);
    }
}
