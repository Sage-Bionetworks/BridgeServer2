package org.sagebionetworks.bridge.models.assessments;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;

@Entity
@Table(name = "AssessmentConfigs")
public class HibernateAssessmentConfig {
    
    public static final HibernateAssessmentConfig create(String guid, AssessmentConfig config) {
        HibernateAssessmentConfig c = new HibernateAssessmentConfig();
        c.setConfig(config.getConfig());
        c.setCreatedOn(config.getCreatedOn());
        c.setModifiedOn(config.getModifiedOn());
        c.setGuid(guid);
        c.setVersion(config.getVersion());
        return c;
    }

    @Id
    private String guid;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode config;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    @Version
    private long version;
    
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public JsonNode getConfig() {
        return config;
    }
    public void setConfig(JsonNode config) {
        this.config = config;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
}
