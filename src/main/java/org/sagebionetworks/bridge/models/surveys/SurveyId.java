package org.sagebionetworks.bridge.models.surveys;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;


@SuppressWarnings("serial")
@Embeddable
public final class SurveyId implements Serializable {
    
    @Column(name = "guid")
    private String guid;

    @Column(name = "createdOn")
    private long createdOn;

    public SurveyId() {
    }
 
    public SurveyId(GuidCreatedOnVersionHolder keys) {
        this.guid = keys.getGuid();
        this.createdOn = keys.getCreatedOn();
    }
    
    public String getSurveyGuid() {
        return guid;
    }
    
    public long getCreatedOn() {
        return createdOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, createdOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyId other = (SurveyId) obj;
        return Objects.equals(guid, other.guid) && Objects.equals(createdOn, other.createdOn);
    }
}
