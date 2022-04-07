package org.sagebionetworks.bridge.models.permissions;

import java.util.Objects;

public final class EntityRef {
    
    private final EntityType entityType;
    private final String entityId;
    private final String entityName;
    
    public EntityRef(EntityType entityType, String entityId, String entityName) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
    }
    
    public EntityType getEntityType() {
        return entityType;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityRef entityRef = (EntityRef) o;
        return entityType == entityRef.entityType && Objects.equals(entityId, entityRef.entityId) && Objects.equals(entityName, entityRef.entityName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, entityName);
    }
}
