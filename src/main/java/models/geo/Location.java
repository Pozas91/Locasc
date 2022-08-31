package models.geo;

public class Location {
    private final String _name;
    private final Double _lat;
    private final Double _long;

    public Location(String name, Double latitude, Double longitude) {
        _name = name;
        _lat = latitude;
        _long = longitude;
    }

    public Location(Location o) {
        _name = o._name;
        _lat = o._lat;
        _long = o._long;
    }

    public String getName() {
        return _name;
    }

    public Double getLat() {
        return _lat;
    }

    public Double getLong() {
        return _long;
    }

    public Double distance(Location location) {
        double dLat = Geo.degreesToRadian(location._lat - _lat), dLong = Geo.degreesToRadian(location._long - _long);

        double a = Math.sin(dLat / 2.) * Math.sin(dLat / 2.) + Math.cos(Geo.degreesToRadian(_lat)) *
            Math.cos(Geo.degreesToRadian(location._lat)) *
            Math.sin(dLong / 2.) * Math.sin(dLong / 2.);

        double c = 2. * Math.atan2(Math.sqrt(a), Math.sqrt(1. - a));
        return Geo.RADIUS_EARTH * c;
    }

    public Double latency(Location location) {
        return Geo.latency(distance(location));
    }

    public static Double latency(Location a, Location b) {
        return Geo.latency(a.distance(b));
    }

    public static Location of(String... attributes) {
        String name = attributes[3];
        double latitude = Double.parseDouble(attributes[1]);
        double longitude = Double.parseDouble(attributes[2]);

        return new Location(name, latitude, longitude);
    }

    public Location copy() {
        return new Location(this);
    }

    @Override
    public String toString() {
        return getName();
    }
}
