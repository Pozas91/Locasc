package models.enums;

public enum ConnRange {
    L0(0, 50.), // Bluetooth v5.0 data rate.
    L1(1, 600.), // WiFi 2.4GHz data rate.
    L2(2, 1_300.), // WiFi 5GHz data rate.
    L3(3, 10_000.), // Ethernet Cat. 7 data rate.
    L4(4, 40_000.), // Ethernet Cat. 8 data rate.
    L5(5, 160_000.); // Optic fiber data rate.

    private final Integer _level;
    private final Double _capacity;

    ConnRange(Integer level, Double capacity) {
        _level = level;
        _capacity = capacity;
    }

    public Integer getLevel() {
        return _level;
    }

    public Double getCapacity() {
        return _capacity;
    }

    public Double getCapacity(ConnRange r1) {
        int min = Math.min(_level, r1._level);
        return getRange(min)._capacity;
    }

    @Override
    public String toString() {
        return name();
    }

    public static ConnRange getRange(Integer level) {
        return switch (level) {
            case 0 -> ConnRange.L0;
            case 1 -> ConnRange.L1;
            case 2 -> ConnRange.L2;
            case 3 -> ConnRange.L3;
            case 4 -> ConnRange.L4;
            case 5 -> ConnRange.L5;
            default -> throw new RuntimeException("Level not recognise");
        };
    }
}
