package org.sagebionetworks.bridge.models.organizations;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class OrganizationId implements Serializable {
    
    @Column(name = "appId")
    private String appId;

    @Column(name = "identifier")
    private String identifier;
    
    public OrganizationId() {
    }
    
    public OrganizationId(String appId, String identifier) {
        this.appId = appId;
        this.identifier = identifier;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, identifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OrganizationId other = (OrganizationId) obj;
        return Objects.equals(appId, other.appId) && Objects.equals(identifier, other.identifier);
    }
}
