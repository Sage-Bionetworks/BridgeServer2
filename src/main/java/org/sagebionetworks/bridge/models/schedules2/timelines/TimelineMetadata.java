package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * The timeline metadata table allows us to take any instance GUID from a timeline
 * that is submitted by a mobile client, and map it back to the provenance we have 
 * as to when and how the data associated with that GUID was collected. We create 
 * these records the first time we create a timeline from a schedule.
 */
@Entity
@Table(name = "TimelineMetadata")
public class TimelineMetadata implements BridgeEntity {
    
    public static final TimelineMetadata copy(TimelineMetadata meta) {
        TimelineMetadata copy = new TimelineMetadata();
        copy.setGuid(meta.getGuid());
        copy.setAssessmentInstanceGuid(meta.getAssessmentInstanceGuid());
        copy.setAssessmentGuid(meta.getAssessmentGuid());
        copy.setAssessmentId(meta.getAssessmentId());
        copy.setAssessmentRevision(meta.getAssessmentRevision());
        copy.setSessionInstanceGuid(meta.getSessionInstanceGuid());
        copy.setSessionGuid(meta.getSessionGuid());
        copy.setSessionInstanceStartDay(meta.getSessionInstanceStartDay());
        copy.setSessionInstanceEndDay(meta.getSessionInstanceEndDay());
        copy.setScheduleGuid(meta.getScheduleGuid());
        copy.setSessionStartEventId(meta.getSessionStartEventId());
        copy.setTimeWindowGuid(meta.getTimeWindowGuid());
        copy.setTimeWindowPersistent(meta.isTimeWindowPersistent());
        copy.setScheduleModifiedOn(meta.getScheduleModifiedOn());
        copy.setSchedulePublished(meta.isSchedulePublished());
        copy.setAppId(meta.getAppId());
        copy.setStudyBurstId(meta.getStudyBurstId());
        copy.setStudyBurstNum(meta.getStudyBurstNum());
        copy.setSessionLabel(meta.getSessionLabel());
        copy.setSessionSymbol(meta.getSessionSymbol());
        return copy;
    }
    
    public final Map<String,String> asMap() {
        Map<String,String> map = new HashMap<>();
        map.put("guid", guid);
        map.put("assessmentInstanceGuid", assessmentInstanceGuid);
        map.put("assessmentGuid", assessmentGuid);
        map.put("assessmentId", assessmentId);
        map.put("assessmentRevision", assessmentRevision == null ? null : Integer.toString(assessmentRevision));
        map.put("sessionInstanceGuid", sessionInstanceGuid);
        map.put("sessionGuid", sessionGuid);
        map.put("sessionInstanceStartDay", sessionInstanceStartDay == null ? null : Integer.toString(sessionInstanceStartDay));
        map.put("sessionInstanceEndDay", sessionInstanceEndDay == null ? null : Integer.toString(sessionInstanceEndDay));
        map.put("sessionStartEventId", sessionStartEventId);
        map.put("timeWindowGuid", timeWindowGuid);
        map.put("timeWindowPersistent", Boolean.toString(timeWindowPersistent));
        map.put("scheduleGuid", scheduleGuid);
        map.put("scheduleModifiedOn", scheduleModifiedOn == null ? null : scheduleModifiedOn.toString());
        map.put("schedulePublished", Boolean.toString(schedulePublished));
        map.put("appId", appId);
        map.put("studyBurstId", studyBurstId);
        map.put("studyBurstNum", studyBurstNum == null ? null : studyBurstNum.toString());
        map.put("sessionLabel", sessionLabel);
        // we don't need to export session symbol...label is debatable
        return map;
    }
    
    @Id
    private String guid;
    private String assessmentInstanceGuid;
    private String assessmentGuid;
    private String assessmentId;
    private Integer assessmentRevision;
    private String sessionInstanceGuid;
    private String sessionGuid;
    private String sessionStartEventId;
    private Integer sessionInstanceStartDay;
    private Integer sessionInstanceEndDay;
    private String timeWindowGuid;
    private boolean timeWindowPersistent;
    private String scheduleGuid;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime scheduleModifiedOn;
    private boolean schedulePublished;
    private String studyBurstId;
    private Integer studyBurstNum;
    private String appId;
    private String sessionSymbol;
    private String sessionLabel;
    
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
    public Integer getAssessmentRevision() {
        return assessmentRevision;
    }
    public void setAssessmentRevision(Integer assessmentRevision) {
        this.assessmentRevision = assessmentRevision;
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
    public String getSessionStartEventId() {
        return sessionStartEventId;
    }
    public void setSessionStartEventId(String sessionStartEventId) {
        this.sessionStartEventId = sessionStartEventId;
    }
    public Integer getSessionInstanceStartDay() {
        return sessionInstanceStartDay;
    }
    public void setSessionInstanceStartDay(Integer startDay) {
        this.sessionInstanceStartDay = startDay;
    }
    public Integer getSessionInstanceEndDay() {
        return sessionInstanceEndDay;
    }
    public void setSessionInstanceEndDay(Integer endDay) {
        this.sessionInstanceEndDay = endDay;
    }
    public String getTimeWindowGuid( ) {
        return timeWindowGuid;
    }
    public void setTimeWindowGuid(String timeWindowGuid) {
        this.timeWindowGuid = timeWindowGuid;
    }
    public boolean isTimeWindowPersistent( ) {
        return timeWindowPersistent;
    }
    public void setTimeWindowPersistent(boolean timeWindowPersistent) {
        this.timeWindowPersistent = timeWindowPersistent;
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
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getStudyBurstId() {
        return studyBurstId;
    }
    public void setStudyBurstId(String studyBurstId) {
        this.studyBurstId = studyBurstId;
    }
    public Integer getStudyBurstNum() {
        return studyBurstNum;
    }
    public void setStudyBurstNum(Integer studyBurstNum) {
        this.studyBurstNum = studyBurstNum;
    }
    public String getSessionSymbol() {
        return sessionSymbol;
    }
    public void setSessionSymbol(String sessionSymbol) {
        this.sessionSymbol = sessionSymbol;
    }
    public String getSessionLabel() {
        return sessionLabel;
    }
    public void setSessionLabel(String sessionLabel) {
        this.sessionLabel = sessionLabel;
    }
}
