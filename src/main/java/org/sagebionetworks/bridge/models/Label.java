package org.sagebionetworks.bridge.models;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(lang, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Label other = (Label) obj;
        return Objects.equals(lang, other.lang) && Objects.equals(value, other.value);
    }
}
