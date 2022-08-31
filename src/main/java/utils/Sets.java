package utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Sets {
    /**
     * Define a new hash set easy
     */
    @SafeVarargs
    public static <T> Set<T> of(T... objs) {
        Set<T> set = new HashSet<>();
        Collections.addAll(set, objs);
        return set;
    }
}
