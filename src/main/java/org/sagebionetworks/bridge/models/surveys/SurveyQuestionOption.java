package org.sagebionetworks.bridge.models.surveys;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public final class SurveyQuestionOption {

    private final String label;
    private final String detail;
    private final String value;
    private final Image image;
    private final Boolean exclusive;
    
    @JsonCreator
    public SurveyQuestionOption(@JsonProperty("label") String label, @JsonProperty("detail") String detail,
            @JsonProperty("value") String value, @JsonProperty("image") Image image,
            @JsonProperty("exclusive") Boolean exclusive) {
        this.label = label;
        this.detail = detail;
        this.value = value;
        this.image = image;
        this.exclusive = exclusive;
    }
    
    public SurveyQuestionOption(String label) {
        this(label, null, label, null, null);
    }
    
    public String getLabel() {
        return label;
    }
    public String getDetail() {
        return detail;
    }
    public String getValue() {
        return StringUtils.isNotBlank(value) ? value : label;
    }
    public Image getImage() {
        return image;
    }
    public Boolean isExclusive() { 
        return exclusive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, detail, value, image, exclusive);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SurveyQuestionOption other = (SurveyQuestionOption) obj;
        return Objects.equals(label, other.label) &&
                Objects.equals(detail, other.detail) &&
                Objects.equals(value, other.value) &&
                Objects.equals(image, other.image) &&
                Objects.equals(exclusive, other.exclusive);
    }

    @Override
    public String toString() {
        return String.format("SurveyQuestionOption [label=%s, detail=%s, value=%s, image=%s, exclusive=%s]", 
            label, detail, value, image, exclusive);
    }
    
}
