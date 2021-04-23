package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;

import org.joda.time.DateTime;

@SuppressWarnings("serial")
@Embeddable
public final class AdherenceRecordId implements Serializable {

    private String userId;
    private String studyId;
    private String guid;
    private DateTime startedOn;

    public AdherenceRecordId() {
    }
 
    public AdherenceRecordId(String userId, String studyId, String guid, DateTime startedOn) {
        this.userId = userId;
        this.studyId = studyId;
        this.guid = guid;
        this.startedOn = startedOn;
    }
    
    public String getUserId() {
        return userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public String getGuid() {
        return guid;
    }
    public DateTime getStartedOn() {
        return startedOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, studyId, guid, startedOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AdherenceRecordId)) {
            return false;
        }
        AdherenceRecordId other = (AdherenceRecordId) obj;
        return Objects.equals(userId, other.userId) &&
                Objects.equals(studyId, other.studyId) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(startedOn, other.startedOn);
    }
}
