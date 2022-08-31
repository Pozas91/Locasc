package utils;

import models.enums.CONFIG;

import java.util.HashMap;
import java.util.Map;

/*
TODO: This class is only for debug purposes, please don't remove.
 */
public final class RunConf {
    private static RunConf instance;
    private final Map<CONFIG, Object> _conf = new HashMap<>();

    public static RunConf instance() {
        if (instance == null) {
            instance = new RunConf();
        }

        return instance;
    }

    public Map<CONFIG, Object> get() {
        return instance._conf;
    }

    public Object getOrDefault(CONFIG param, Object def) {
        return get().getOrDefault(param, def);
    }

    public Boolean getBoolean(CONFIG param) {
        return (Boolean) get().getOrDefault(param, false);
    }

    public void set(CONFIG param, Object value) {
        instance._conf.put(param, value);
    }
}
