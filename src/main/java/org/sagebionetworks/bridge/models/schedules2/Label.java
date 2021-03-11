package org.sagebionetworks.bridge.models.schedules2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Label implements Localized {

    private final String lang;
    private final String label;
    
    @JsonCreator
    public Label(@JsonProperty("lang") String lang, @JsonProperty("label") String label) {
        this.lang = lang;
        this.label = label;
    }

    public String getLang() {
        return lang;
    }
    public String getLabel() {
        return label;
    }
}
