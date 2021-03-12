package org.sagebionetworks.bridge.models.schedules2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Label implements HasLang {

    private final String lang;
    private final String value;
    
    @JsonCreator
    public Label(@JsonProperty("lang") String lang, @JsonProperty("value") String value) {
        this.lang = lang;
        this.value = value;
    }

    public String getLang() {
        return lang;
    }
    public String getValue() {
        return value;
    }
}
