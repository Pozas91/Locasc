package generators;

import models.enums.QoS;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Weights {
    public static Map<QoS, Double> get(List<QoS> attributes) {
        double weight = 1. / attributes.size();
        return attributes.parallelStream().collect(Collectors.toMap(k -> k, v -> weight));
    }

    public static Map<QoS, Double> get(List<QoS> qos, List<Double> weights) {
        return IntStream.range(0, qos.size()).boxed().collect(Collectors.toMap(qos::get, weights::get));
    }
}
