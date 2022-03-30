package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.util.Objects;

@Entity
@Table(name = "Permissions")
public final class Permission implements BridgeEntity {
    
    @Id
    private String guid;
    @JsonIgnore
    private String appId;
    private String userId;
    @Enumerated(EnumType.STRING)
    private AccessLevel accessLevel;
    @Enumerated(EnumType.STRING)
    private EntityType entityType;
    private String entityId;
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
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public AccessLevel getAccessLevel() {
        return accessLevel;
    }
    
    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
    
    public EntityType getEntityType() {
        return entityType;
    }
    
    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return version == that.version &&
                Objects.equals(guid, that.guid) &&
                Objects.equals(appId, that.appId) &&
                Objects.equals(userId, that.userId) &&
                accessLevel == that.accessLevel &&
                entityType == that.entityType &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(createdOn, that.createdOn) &&
                Objects.equals(modifiedOn, that.modifiedOn);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(guid, appId, userId, accessLevel, entityType, entityId, createdOn, modifiedOn, version);
    }
}
