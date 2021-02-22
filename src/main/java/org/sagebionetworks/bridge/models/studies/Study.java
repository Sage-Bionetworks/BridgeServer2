package org.sagebionetworks.bridge.models.studies;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.HibernateStudy;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=HibernateStudy.class)
public interface Study extends BridgeEntity {
    
    public static Study create() {
        return new HibernateStudy();
    }

    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getAppId();
    void setAppId(String appId);
    
    String getName();
    void setName(String name);
    
    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);
    
    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);
    
    JsonNode getClientData();
    void setClientData(JsonNode clientData);
    
    Long getVersion();
    void setVersion(Long version);
    
}
