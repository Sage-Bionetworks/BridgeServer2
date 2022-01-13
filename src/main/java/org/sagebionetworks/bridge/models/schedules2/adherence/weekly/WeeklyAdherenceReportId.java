package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class WeeklyAdherenceReportId implements Serializable {

    private final String appId;
    
    private final String studyId;
    
    private final String userId;

    // This makes Hibernate happy, and also passes EqualsVerifier tests.
    @SuppressWarnings("unused")
    private WeeklyAdherenceReportId() {
        this(null, null, null);
    }
    
    public WeeklyAdherenceReportId(String appId, String studyId, String userId) {
        this.appId = appId;
        this.studyId = studyId;
        this.userId = userId;
    }
    public String getAppId() {
        return appId;
    }
    public String getStudyId() {
        return studyId;
    }
    public String getUserId() {
        return userId;
    }
    @Override
    public int hashCode() {
        return Objects.hash(appId, studyId, userId);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        WeeklyAdherenceReportId other = (WeeklyAdherenceReportId) obj;
        return Objects.equals(appId, other.appId) && 
                Objects.equals(studyId, other.studyId) && 
                Objects.equals(userId, other.userId);
    }
}
