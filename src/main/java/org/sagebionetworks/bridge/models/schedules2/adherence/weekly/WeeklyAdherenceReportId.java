package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public class WeeklyAdherenceReportId implements Serializable {

    private String appId;
    
    private String userId;
    
    private String studyId;
    
    public WeeklyAdherenceReportId() {
    }
    public WeeklyAdherenceReportId(String appId, String userId, String studyId) {
        this.appId = appId;
        this.userId = userId;
        this.studyId = studyId;
    }
    public String getAppId() {
        return appId;
    }
    public String getUserId() {
        return userId;
    }
    public String getStudyId() {
        return studyId;
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
