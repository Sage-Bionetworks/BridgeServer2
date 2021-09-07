package org.sagebionetworks.bridge.models.appconfig;

import java.util.List;

import org.sagebionetworks.bridge.models.Label;

public class AppConfigEnumEntry {

    private String value;
    private List<Label> labels;

    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public List<Label> getLabels() {
        return labels;
    }
    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
}
