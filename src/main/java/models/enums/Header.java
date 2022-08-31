package models.enums;

public enum Header {
    SERVICES, PROVIDERS, GENERATIONS, BATCH_SIZE, EXECUTION_TIME, BEST_FITNESS, MEAN_FITNESS, WORST_FITNESS,
    PARALLELS_PATTERNS, ITERATIVE_PATTERNS, CONDITIONALS_PATTERNS, SEQUENTIAL_PATTERNS, GENOTYPE, SUB_PROBLEMS,
    BEST_PROVIDER, BEST_PROVIDER_VALUE, WORST_PROVIDER, WORST_PROVIDER_VALUE, LIMIT_TIME, MUTATION_PROB, CROSSOVER_PROB,
    CROSSOVER_POINTS, POPULATION, ELITE_COUNT, PROVIDERS_METHOD, SLOPE, INTERCEPT, SPLIT_PARALLELS, VARIABLE_PROVIDERS,
    UTILITY_FITNESS, HEURISTIC, BEST_FITNESS_UNLIMITED, CONSTRAINT_FITNESS, FITNESS, CONVERGENCE, RESOLVER, BEST_BOUND,
    WORST_BOUND, ID, TIME, PRE_CALCULATION_TIME, UTILITY_GA_TIME, FITNESS_WITH_UT_TIME, U_DEGREES, STEADY_GENERATIONS;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
