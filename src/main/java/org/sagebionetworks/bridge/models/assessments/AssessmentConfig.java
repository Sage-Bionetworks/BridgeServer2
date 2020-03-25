package org.sagebionetworks.bridge.models.assessments;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;

public class AssessmentConfig {

    public static final AssessmentConfig create(HibernateAssessmentConfig config) {
        AssessmentConfig c = new AssessmentConfig();
        c.setConfig(config.getConfig());
        c.setCreatedOn(config.getCreatedOn());
        c.setModifiedOn(config.getModifiedOn());
        c.setVersion(config.getVersion());
        return c;
    }

    private JsonNode config;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private long version;

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
