package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;

@SuppressWarnings("serial")
@Embeddable
public final class AdherenceRecordId implements Serializable {

    private String userId;
    private String studyId;
    private String instanceGuid;
    @Column(name = "startedOn")
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime startedOn;

    public AdherenceRecordId() {
    }
 
    public AdherenceRecordId(String userId, String studyId, String instanceGuid, DateTime startedOn) {
        this.userId = userId;
        this.studyId = studyId;
        this.instanceGuid = instanceGuid;
        this.startedOn = startedOn;
    }
    
    public String getUserId() {
        return userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public String getInstanceGuid() {
        return instanceGuid;
    }
    public DateTime getStartedOn() {
        return startedOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, studyId, instanceGuid, startedOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AdherenceRecordId)) {
            return false;
        }
        AdherenceRecordId other = (AdherenceRecordId) obj;
        return Objects.equals(userId, other.userId) &&
                Objects.equals(studyId, other.studyId) &&
                Objects.equals(instanceGuid, other.instanceGuid) &&
                Objects.equals(startedOn, other.startedOn);
    }
}
