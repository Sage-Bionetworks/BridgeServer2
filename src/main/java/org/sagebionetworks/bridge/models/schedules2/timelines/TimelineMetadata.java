package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;

/**
 * The timeline metadata table allows us to take any instance GUID from a timeline
 * that is submitted by a mobile client, and map it back to the provenance we have 
 * as to when and how the data associated with that GUID was collected. We create 
 * these records the first time we create a timeline from a schedule.
 */
@Entity
@Table(name = "TimelineMetadata")
public class TimelineMetadata {
    
    public static final TimelineMetadata copy(TimelineMetadata meta) {
        TimelineMetadata copy = new TimelineMetadata();
        copy.setGuid(meta.getGuid());
        copy.setAssessmentInstanceGuid(meta.getAssessmentInstanceGuid());
        copy.setAssessmentGuid(meta.getAssessmentGuid());
        copy.setAssessmentId(meta.getAssessmentId());
        copy.setSessionInstanceGuid(meta.getSessionInstanceGuid());
        copy.setSessionGuid(meta.getSessionGuid());
        copy.setScheduleGuid(meta.getScheduleGuid());
        copy.setScheduleModifiedOn(meta.getScheduleModifiedOn());
        copy.setSchedulePublished(meta.isSchedulePublished());
        if (meta.getStudyIds() != null) {
            Set<String> set = new HashSet<>();
            set.addAll(meta.getStudyIds());
            copy.setStudyIds(set);
        }
        copy.setAppId(meta.getAppId());
        return copy;
    }
    
    @Id
    private String guid;
    private String assessmentInstanceGuid;
    private String assessmentGuid;
    private String assessmentId;
    private String sessionInstanceGuid;
    private String sessionGuid;
    private String scheduleGuid;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime scheduleModifiedOn;
    private boolean schedulePublished;
    /**
     * A timeline is generated 1:1 from a schedule, but schedule can be used in
     * more than one study (or study arm). The study IDs for this record will be
     * the intersection of the participant’s enrolled studies, and the schedules
     * that apply to the participant through these study relationships.
     */
    @Transient
    private Set<String> studyIds;
    private String appId;
    
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getAssessmentInstanceGuid() {
        return assessmentInstanceGuid;
    }
    public void setAssessmentInstanceGuid(String assessmentInstanceGuid) {
        this.assessmentInstanceGuid = assessmentInstanceGuid;
    }
    public String getAssessmentGuid() {
        return assessmentGuid;
    }
    public void setAssessmentGuid(String assessmentGuid) {
        this.assessmentGuid = assessmentGuid;
    }
    public String getAssessmentId() {
        return assessmentId;
    }
    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }
    public String getSessionInstanceGuid() {
        return sessionInstanceGuid;
    }
    public void setSessionInstanceGuid(String sessionInstanceGuid) {
        this.sessionInstanceGuid = sessionInstanceGuid;
    }
    public String getSessionGuid() {
        return sessionGuid;
    }
    public void setSessionGuid(String sessionGuid) {
        this.sessionGuid = sessionGuid;
    }
    public String getScheduleGuid() {
        return scheduleGuid;
    }
    public void setScheduleGuid(String scheduleGuid) {
        this.scheduleGuid = scheduleGuid;
    }
    public DateTime getScheduleModifiedOn() {
        return scheduleModifiedOn;
    }
    public void setScheduleModifiedOn(DateTime scheduleModifiedOn) {
        this.scheduleModifiedOn = scheduleModifiedOn;
    }
    public boolean isSchedulePublished() {
        return schedulePublished;
    }
    public void setSchedulePublished(boolean schedulePublished) {
        this.schedulePublished = schedulePublished;
    }
    public Set<String> getStudyIds() {
        return studyIds;
    }
    public void setStudyIds(Set<String> studyIds) {
        this.studyIds = studyIds;
    }
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }

}
