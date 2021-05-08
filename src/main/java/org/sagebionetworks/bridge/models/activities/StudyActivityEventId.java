package org.sagebionetworks.bridge.models.activities;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;

@SuppressWarnings("serial")
@Embeddable
public final class StudyActivityEventId implements Serializable {

    private String userId;
    private String studyId;
    private String eventId;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Column(name = "eventTimestamp")
    private DateTime timestamp;
    
    public StudyActivityEventId() {}
    
    public StudyActivityEventId(String userId, String studyId, String eventId, DateTime timestamp) {
        this.userId = userId;
        this.studyId = studyId;
        this.eventId = eventId;
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }
    public String getStudyId() {
        return studyId;
    }
    public String getEventId() {
        return eventId;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, studyId, eventId, timestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StudyActivityEventId)) {
            return false;
        }
        StudyActivityEventId other = (StudyActivityEventId) obj;
        return Objects.equals(eventId, other.eventId) &&
                Objects.equals(studyId, other.studyId) &&
                Objects.equals(timestamp, other.timestamp) &&
                Objects.equals(userId, other.userId);
    }
}