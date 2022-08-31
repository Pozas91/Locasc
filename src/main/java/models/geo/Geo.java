package models.geo;

public abstract class Geo {
    public static final int RADIUS_EARTH = 6371;
    private static final double LATENCY_FACTOR = 5E-6;

    public static Double degreesToRadian(Double degrees) {
        return degrees * (Math.PI / 180.);
    }

    /**
     * Because light travels approximately 1.5 times slower through optical fiber than in a vacuum, the latency is
     * 5 µsec per kilometer.
     *
     * @param distance Distance in km between two points
     * @return latency in s given a distance
     */
    public static Double latency(Double distance) {
        return LATENCY_FACTOR * distance;
    }
}
