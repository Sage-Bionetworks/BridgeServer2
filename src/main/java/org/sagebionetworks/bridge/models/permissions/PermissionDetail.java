package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.accounts.AccountRef;

import java.util.Objects;

@JsonPropertyOrder({ "guid", "accessLevel", "account", "entity", "createdOn", "modifiedOn", "version" })
public final class PermissionDetail {
    
    private final String guid;
    private final AccessLevel accessLevel;
    private final AccountRef account;
    private final EntityRef entity;
    private final DateTime createdOn;
    private final DateTime modifiedOn;
    private final long version;
    
    public PermissionDetail(Permission permission, EntityRef entity, AccountRef account) {
        this.guid = permission.getGuid();
        this.accessLevel = permission.getAccessLevel();
        this.entity = entity;
        this.account = account;
        this.createdOn = permission.getCreatedOn();
        this.modifiedOn = permission.getModifiedOn();
        this.version = permission.getVersion();
    }
    
    public String getGuid() {
        return guid;
    }
    
    public AccessLevel getAccessLevel() {
        return accessLevel;
    }
    
    public AccountRef getAccount() {
        return account;
    }
    
    public EntityRef getEntity() {
        return entity;
    }
    
    public DateTime getCreatedOn() {
        return createdOn;
    }
    
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    
    public long getVersion() {
        return version;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionDetail that = (PermissionDetail) o;
        return version == that.version &&
                Objects.equals(guid, that.guid) &&
                accessLevel == that.accessLevel &&
                Objects.equals(account, that.account) &&
                Objects.equals(entity, that.entity) &&
                Objects.equals(createdOn, that.createdOn) &&
                Objects.equals(modifiedOn, that.modifiedOn);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(guid, accessLevel, account, entity, createdOn, modifiedOn, version);
    }
    
}
