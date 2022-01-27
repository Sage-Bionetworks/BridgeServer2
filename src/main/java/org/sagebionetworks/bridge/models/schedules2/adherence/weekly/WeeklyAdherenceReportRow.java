package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.Objects;

/**
 * A substantial amount of metadata is moved from being repeated in the EventStreamDay element
 * to a separate row metdata element, which helps in UI tools to lay out the report.
 */
public final class WeeklyAdherenceReportRow {
    private String label;
    private String searchableLabel;
    private String sessionGuid;
    private String startEventId;
    private String sessionName;
    private String sessionSymbol;
    private Integer week;
    private String studyBurstId;
    private Integer studyBurstNum;
    
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public String getSearchableLabel() {
        return searchableLabel;
    }
    public void setSearchableLabel(String searchableLabel) {
        this.searchableLabel = searchableLabel;
    }
    public String getSessionGuid() {
        return sessionGuid;
    }
    public void setSessionGuid(String sessionGuid) {
        this.sessionGuid = sessionGuid;
    }
    public String getStartEventId() {
        return startEventId;
    }
    public void setStartEventId(String startEventId) {
        this.startEventId = startEventId;
    }
    public String getSessionName() {
        return sessionName;
    }
    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public String getSessionSymbol() {
        return sessionSymbol;
    }
    public void setSessionSymbol(String sessionSymbol) {
        this.sessionSymbol = sessionSymbol;
    }
    public Integer getWeek() {
        return week;
    }
    public void setWeek(Integer week) {
        this.week = week;
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
    @Override
    public int hashCode() {
        return Objects.hash(label, searchableLabel, sessionGuid, startEventId, sessionName, sessionSymbol, studyBurstId,
                studyBurstNum, week);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        WeeklyAdherenceReportRow other = (WeeklyAdherenceReportRow) obj;
        return Objects.equals(label, other.label) && Objects.equals(searchableLabel, other.searchableLabel)
                && Objects.equals(sessionGuid, other.sessionGuid) && Objects.equals(startEventId, other.startEventId)
                && Objects.equals(sessionName, other.sessionName) && Objects.equals(sessionSymbol, other.sessionSymbol)
                && Objects.equals(studyBurstId, other.studyBurstId)
                && Objects.equals(studyBurstNum, other.studyBurstNum) && Objects.equals(week, other.week);
    }
}
