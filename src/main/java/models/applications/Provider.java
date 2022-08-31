package models.applications;

import models.enums.ConnRange;
import models.enums.QoS;
import models.geo.Location;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

// This class is called `ConcreteService` in papers that we've read.
public class Provider implements Serializable {
    private final String _name;
    private Map<QoS, Double> _attributes, _normalized;
    private final Location _location;
    private final ConnRange _connRange;

    public Provider(String name, Map<QoS, Double> attributes, Location location, ConnRange connRange) {
        _name = name;
        _attributes = attributes;
        _normalized = new HashMap<>(attributes);
        _location = location;
        _connRange = connRange;
    }

    public Provider(String name, Map<QoS, Double> attributes, Location location) {
        this(name, attributes, location, ConnRange.L0);
    }

    public Provider(String name, Map<QoS, Double> attributes) {
        this(name, attributes, null);
    }

    public Provider(Provider o) {
        _name = o._name;
        _attributes = new HashMap<>(o._attributes);
        _normalized = new HashMap<>(o._normalized);
        _location = o._location;
        _connRange = o._connRange;
    }

    public String getName() {
        return _name;
    }

    public Location getLocation() {
        return _location;
    }

    public ConnRange getConnRange() {
        return _connRange;
    }

    public Map<QoS, Double> getAttributes() {
        return _attributes;
    }

    public Map<QoS, Double> getNormalized() {
        return _normalized;
    }

    public Double getAttributeValue(QoS attribute) {
        return _attributes.get(attribute);
    }

    public Double getNormalizedValue(QoS attribute) {
        return _normalized.get(attribute);
    }

    public void setNormalizedValue(QoS attribute, Double value) {
        _normalized.put(attribute, value);
    }

    public void setNormalized(Map<QoS, Double> normalized) {
        _normalized = normalized;
    }

    /**
     * Calculate the value for this provider
     *
     * @param weights Map of weights
     * @return Value calculated for this provider
     */
    public Double value(Map<QoS, Double> weights) {
        return _normalized
            .entrySet()
            .parallelStream()
            .mapToDouble(a -> a.getValue() * weights.get(a.getKey()))
            .sum();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(), attributes = new StringBuilder();
        builder.append(String.format("Provider{name: '%s', attributes: ", _name));

        for (Map.Entry<QoS, Double> attribute : _attributes.entrySet()) {
            attributes.append(String.format("%s: %.3f, ", attribute.getKey().name(), attribute.getValue()));
        }

        if (attributes.length() > 1) {
            attributes.delete(attributes.length() - 2, attributes.length());
        }

        // Append attributes
        builder.append(attributes);

        // Add location if exists
        if (_location != null) {
            builder.append(String.format(", location: '%s'", _location.getName()));
        }

        // Add connection type if exists
        if (_connRange != null) {
            builder.append(String.format(", connection: '%s'", _connRange));
        }

        builder.append("}");

        return builder.toString();
    }

    public Provider copy() {
        return new Provider(this);
    }
}
