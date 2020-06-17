package org.sagebionetworks.bridge.models.organizations;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;

@Entity
@Table(name = "Organizations")
@IdClass(OrganizationId.class)
@BridgeTypeName("Organization")
public class HibernateOrganization implements Organization {

    @JsonIgnore
    @Id
    private String appId;
    @Id
    private String identifier;
    private String name;
    private String description;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    @Version
    private Long version;
    
    public HibernateOrganization() {
    }
    
    /** A constructor for a summary version of the organization object, that 
     * all users can access through the "list" DAO call.
     */
    public HibernateOrganization(String name, String identifier, String description) {
        this.name = name;
        this.identifier = identifier;
        this.description = description;
    }
    
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String id) {
        this.identifier = id;
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
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
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

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}
