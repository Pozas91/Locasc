package models.enums;

public enum ConnectionType {
    BLUETOOTH(200., 50.),
    // Wifi 5GHz    ->   15-30m
    WIFI_5GHz(30., 1300.),
    // Wifi 2.4GHz  ->   45-90m
    WIFI_24GHz(90., 867.),
    // Data from: https://www.telecocable.com/faq 10GB-BASE/ew
    FIBER(80_000., 10_000.);

    // Define max distance and capacity
    private final Double _distance;
    private final Double _capacity;

    ConnectionType(Double distance, Double capacity) {
        _distance = distance;
        _capacity = capacity;
    }

    public Double getDistance() {
        return _distance;
    }

    public Double getCapacity() {
        return _capacity;
    }

    @Override
    public String toString() {
        return name();
    }
}
