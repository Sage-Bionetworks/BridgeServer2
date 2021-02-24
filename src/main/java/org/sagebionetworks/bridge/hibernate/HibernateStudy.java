package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Version;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyId;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@Entity
@Table(name = "Substudies")
@IdClass(StudyId.class)
@BridgeTypeName("Study")
public class HibernateStudy implements Study {
    @Id
    @Column(name = "id")
    private String identifier;
    @Id
    private String appId;
    private String name;
    private boolean deleted;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    @Column(columnDefinition = "mediumtext", name = "clientData", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode clientData;
    @Version
    private Long version;
    
    /**
     * For full construction of object by Hibernate.
     */
    public HibernateStudy() {}
    
    /**
     * For partial construction of object by Hibernate, excluding expensive fields like clientData.
     */
    public HibernateStudy(String name, String identifier, String appId, boolean deleted, Long version) {
        this.name = name;
        this.identifier = identifier;
        this.appId = appId;
        this.deleted = deleted;
        this.version = version;
    }
    
    @Override
    @JsonAlias("id")
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @JsonIgnore
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }
    
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    @Override
    public JsonNode getClientData() {
        return clientData;
    }
    
    @Override
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    
    @Override
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public DateTime getModifiedOn() {
        return modifiedOn;
    }

    @Override
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
}
