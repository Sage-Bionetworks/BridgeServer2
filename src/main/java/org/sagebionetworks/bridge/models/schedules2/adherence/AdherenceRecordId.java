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
    @Column(name = "eventTimestamp")
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime eventTimestamp;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime instanceTimestamp;

    public AdherenceRecordId() {
    }
 
    public AdherenceRecordId(String userId, String studyId, String instanceGuid, DateTime eventTimestamp,
            DateTime instanceTimestamp) {
        this.userId = userId;
        this.studyId = studyId;
        this.instanceGuid = instanceGuid;
        this.eventTimestamp = eventTimestamp;
        this.instanceTimestamp = instanceTimestamp;
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
    public DateTime getEventTimestamp() {
        return eventTimestamp;
    }
    public DateTime getInstanceTimestamp() {
        return instanceTimestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, studyId, instanceGuid, eventTimestamp, instanceTimestamp);
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
                Objects.equals(eventTimestamp, other.eventTimestamp) &&
                Objects.equals(instanceTimestamp, other.instanceTimestamp);
    }
}
