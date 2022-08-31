package utils;

import models.enums.Header;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Data {
    /**
     * Return a map to store data, where keys are indicated as param and values are ArrayList()
     *
     * @param headers List of keys for data store
     * @return A dictionary
     */
    public static Map<Header, List<Object>> getDataMap(List<Header> headers) {
        return headers.parallelStream().parallel().collect(Collectors.toMap(key -> key, val -> new ArrayList<>()));
    }
}
