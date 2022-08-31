package generators;

import models.enums.Header;

import java.util.Arrays;
import java.util.List;

public class Headers {
    public static List<Header> all() {
        return Arrays.asList(
            Header.SERVICES, Header.PROVIDERS, Header.BATCH_SIZE, Header.LIMIT_TIME, Header.SLOPE, Header.INTERCEPT,
            Header.GENERATIONS, Header.EXECUTION_TIME, Header.SUB_PROBLEMS, Header.BEST_PROVIDER,
            Header.BEST_PROVIDER_VALUE, Header.WORST_PROVIDER, Header.WORST_PROVIDER_VALUE, Header.WORST_FITNESS,
            Header.MEAN_FITNESS, Header.BEST_FITNESS, Header.SEQUENTIAL_PATTERNS, Header.CONDITIONALS_PATTERNS,
            Header.ITERATIVE_PATTERNS, Header.PARALLELS_PATTERNS, Header.GENOTYPE, Header.SPLIT_PARALLELS,
            Header.VARIABLE_PROVIDERS
        );
    }
}
