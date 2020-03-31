package org.sagebionetworks.bridge.models.assessments;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class AssessmentResourceId implements Serializable {

    @Column(name = "appId")
    private String appId;

    @Column(name = "guid")
    private String guid;
    
    public AssessmentResourceId() {
    }
    public AssessmentResourceId(String appId, String guid) {
        this.appId = appId;
        this.guid = guid;
    }
    
    public String getAppId() {
        return appId;
    }
    public String getGuid() {
        return guid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, guid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AssessmentResourceId other = (AssessmentResourceId) obj;
        return Objects.equals(appId, other.appId) && Objects.equals(guid, other.guid);
    }    
}
