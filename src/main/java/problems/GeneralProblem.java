package problems;

import io.jenetics.IntegerGene;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Problem;

import java.util.function.Function;

public abstract class GeneralProblem<T> implements Problem<T, IntegerGene, Double> {
    protected Codec<T, IntegerGene> _codec;

    @Override
    public abstract Double fitness(T arg);

    @Override
    public Function<T, Double> fitness() {
        return this::fitness;
    }

    @Override
    public Codec<T, IntegerGene> codec() {
        return _codec;
    }
}
