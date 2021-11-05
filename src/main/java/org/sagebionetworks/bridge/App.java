package org.sagebionetworks.bridge;

import java.time.Period;

public class App {

    // cumulativeInterval
    // periodFromOrigin
    public static void main(String[] args) {
        Period period = Period.parse("P1D");
        
        period = period.plus(Period.parse("P1W"));
        
        System.out.println(period);
    }
}
