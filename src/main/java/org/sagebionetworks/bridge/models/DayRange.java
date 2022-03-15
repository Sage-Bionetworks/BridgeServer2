package org.sagebionetworks.bridge.models;

public final class DayRange {
    private final int min;
    private final int max;
    public DayRange(int min, int max) {
        this.min = min;
        this.max = max;
    }
    public int getMin() { return min; }
    public int getMax() { return max; }
}
