package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joda.time.LocalDate;

import com.google.common.collect.ImmutableList;

public final class EventStreamDay {
    private String label;
    private boolean today;
    private String sessionGuid;
    private String sessionName;
    private String sessionSymbol;
    private String startEventId;
    private Integer week;
    private String studyBurstId;
    private Integer studyBurstNum;
    private Integer startDay;
    private LocalDate startDate;
    private Map<String,EventStreamWindow> timeWindows;
    
    public EventStreamDay() { 
        timeWindows = new HashMap<>();
    }
    public boolean isToday() {
        return today;
    }
    public void setToday(boolean today) {
        this.today = today;
    }
    public String getSessionGuid() {
        return sessionGuid;
    }
    public void setSessionGuid(String sessionGuid) {
        this.sessionGuid = sessionGuid;
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
    public void setSessionSymbol(String sesionSymbol) {
        this.sessionSymbol = sesionSymbol;
    }
    public String getStartEventId() {
        return startEventId;
    }
    public void setStartEventId(String startEventId) {
        this.startEventId = startEventId;
    }
    public Integer getStartDay() {
        return startDay;
    }
    public void setStartDay(Integer startDay) {
        this.startDay = startDay;
    }
    public LocalDate getStartDate() {
        return startDate;
    }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
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
    public List<EventStreamWindow> getTimeWindows() {
        return ImmutableList.copyOf(timeWindows.values());
    }
    public void setTimeWindows(List<EventStreamWindow> timeWindows) {
        if (timeWindows != null) {
            for (EventStreamWindow window : timeWindows) {
                addTimeWindow(window);
            }
        }
    }
    public void addTimeWindow(EventStreamWindow timeWindowEntry) {
        this.timeWindows.put(timeWindowEntry.getTimeWindowGuid(), timeWindowEntry);
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    @Override
    public int hashCode() {
        return Objects.hash(label, sessionGuid, startEventId, sessionName, sessionSymbol, startDate, startDay,
                studyBurstId, studyBurstNum, timeWindows, week, today);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EventStreamDay other = (EventStreamDay) obj;
        return Objects.equals(label, other.label) && Objects.equals(sessionGuid, other.sessionGuid)
                && Objects.equals(startEventId, other.startEventId) && Objects.equals(sessionName, other.sessionName)
                && Objects.equals(sessionSymbol, other.sessionSymbol) && Objects.equals(startDate, other.startDate)
                && Objects.equals(startDay, other.startDay) && Objects.equals(studyBurstId, other.studyBurstId)
                && Objects.equals(studyBurstNum, other.studyBurstNum) && Objects.equals(timeWindows, other.timeWindows)
                && Objects.equals(week, other.week) && Objects.equals(today, other.today);
    }
}
