package org.sagebionetworks.bridge.models.assessments.config;

import java.util.Objects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = PropertyInfo.Builder.class)
public final class PropertyInfo {
    /**
     * The property name in the JSON object
     */
    private final String propName; 
    /**
     * A human readable label for this field
     */
    private final String label; 
    /**
     * A description to a human editor as to what is allowable in this 
     * field, e.g. "This field must include a number from 1-5."
     */
    private final String description;
    /**
     * Type of data being collected. This indicates the JSON type; the Bridge Study Manager
     * supports 'string', 'boolean' and 'number' (integer) to start.
     */
    private final String propType;
    
    private PropertyInfo(String propName, String label, String description, String propType) {
        this.propName = propName;
        this.label = label;
        this.description = description;
        this.propType = propType;
    }
    
    public String getPropName() {
        return propName;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getPropType() {
        return propType;
    }

    public static class Builder {
        private String propName;
        private String label; 
        private String description;
        private String propType;
        
        public Builder withPropName(String propName) {
            this.propName = propName;
            return this;
        }
        public Builder withLabel(String label) {
            this.label = label;
            return this;
        }
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        public Builder withPropType(String propType) {
            this.propType = propType;
            return this;
        }
        public PropertyInfo build() {
            return new PropertyInfo(propName, label, description, propType);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(propName, label, description, propType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        PropertyInfo other = (PropertyInfo) obj;
        return Objects.equals(propName, other.propName) &&
                Objects.equals(label, other.label) &&
                Objects.equals(description, other.description) &&
                Objects.equals(propType, other.propType);
    }
}
