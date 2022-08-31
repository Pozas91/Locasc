package executions;

import generators.Applications;
import generators.ArchitectureProbabilities;
import generators.Providers;
import generators.Services;
import models.analyzers.Extractor;
import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;
import models.auxiliary.Range;
import models.auxiliary.TimeLimit;
import models.enums.ArchitecturePattern;
import models.enums.Header;
import models.enums.NormalizedMethod;
import models.enums.QoS;
import resolvers.DAC;
import utils.Data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Analyzer {
    // Define logger
    private static final Logger logger = Logger.getLogger(String.valueOf(Analyzer.class));

    public static void main(String[] args) {
        int nOfServicesStart = 10, nOfServicesStop = 100, nOfServicesIncrement = 50;
        int repetitions = 20, nOfProviders = 10;

        for (int nOfServices = nOfServicesStart; nOfServices < nOfServicesStop; nOfServices += nOfServicesIncrement) {
            for (int i = 0; i < repetitions; i++) {
                launch(nOfServices, nOfProviders);
            }
        }
    }

    private static void launch(Integer nOfServices, Integer nOfProviders) {
        // Generic variables
        List<QoS> qos = List.of(QoS.COST, QoS.RESPONSE_TIME);
        long seed = -1, slope = 40, intercept = 30;
        NormalizedMethod nMethod = NormalizedMethod.MAX;
        Range<Integer> providersPerService = new Range<>(10), componentsPerArchitecture = new Range<>(2, 10);

        // Get thread id
        long threadId = Thread.currentThread().getId();

        // Generate static providers
        List<Provider> providers = Providers.get(nOfProviders, qos, 0L);

        // Generate dynamic services
        List<Service> services = Services.get(nOfServices, providers, providersPerService, seed);

        // Specify architectures probabilities
        Map<ArchitecturePattern, Double> architectureProbabilities = ArchitectureProbabilities.get();

        // Get application to work with it
        Application app = Applications.get(
            providers, services, qos, seed, nMethod, architectureProbabilities, componentsPerArchitecture
        );

        // Define 40 slope/30 intercept
        TimeLimit timeLimit = new TimeLimit(slope, intercept);

        // Data to save
        List<Header> headers = List.of(Header.SERVICES, Header.EXECUTION_TIME, Header.GENOTYPE);
        Map<Header, List<Object>> data = Data.getDataMap(headers);

        // Launch application resolve
        logger.info("Starting resolve...");
        DAC.resolve(app, 1, data, timeLimit, false, threadId);
        logger.info("Resolved");

        // Extract genotype
        List<Integer> genotype = cleanGenotype(data);

        // Convert architecture into json file
        Extractor extractor = new Extractor(app, "csv");
        extractor.extractSolution("solution", genotype);
    }

    private static List<Integer> cleanGenotype(Map<Header, List<Object>> data) {
        String genotypeStr = String.valueOf(data.get(Header.GENOTYPE).get(0));
        genotypeStr = genotypeStr.substring(1, genotypeStr.length() - 1);
        return Arrays.stream(genotypeStr.split(","))
            .map(k -> Integer.parseInt(k.strip()))
            .collect(Collectors.toList());
    }
}
