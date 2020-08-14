package org.sagebionetworks.bridge.models.studies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class StudyId implements Serializable {
    
    @Column(name = "studyId")
    private String appId;

    @Column(name = "id")
    private String id;

    public StudyId() {
    }
 
    public StudyId(String appId, String id) {
        this.appId = appId;
        this.id = id;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        StudyId other = (StudyId) obj;
        return Objects.equals(appId, other.appId) && Objects.equals(id, other.id);
    }
}
