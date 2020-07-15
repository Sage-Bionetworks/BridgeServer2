package org.sagebionetworks.bridge.hibernate;

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

@Entity
@Table(name = "Substudies")
@IdClass(StudyId.class)
@BridgeTypeName("Study")
public class HibernateStudy implements Study {
    @Id
    private String identifier;
    @Id
    private String appId;
    private String name;
    private boolean deleted;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    @Version
    private Long version;
    
    @Override
    @JsonAlias("id")
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String id) {
        this.identifier = id;
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
