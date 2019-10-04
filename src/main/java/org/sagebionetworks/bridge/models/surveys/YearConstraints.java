package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.NUMBERFIELD;
import static org.sagebionetworks.bridge.models.surveys.UIHint.SLIDER;
import static org.sagebionetworks.bridge.models.surveys.UIHint.YEAR;

import java.util.EnumSet;

public class YearConstraints extends TimeBasedConstraints {

    private String earliestValue;
    private String latestValue;
    
    public YearConstraints() {
        setDataType(DataType.YEAR);
        setSupportedHints(EnumSet.of(YEAR, NUMBERFIELD, SLIDER));
    }
    public String getEarliestValue() {
        return earliestValue;
    }
    public void setEarliestValue(String earliestValue) {
        this.earliestValue = earliestValue;
    }
    public String getLatestValue() {
        return latestValue;
    }
    public void setLatestValue(String latestValue) {
        this.latestValue = latestValue;
    }    
}
