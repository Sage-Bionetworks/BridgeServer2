package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;

public class EventStreamWindow {
    private String sessionInstanceGuid;
    private String timeWindowGuid;
    private SessionCompletionState state;
    private Integer endDay;
    private LocalDate endDate;
    
    public String getSessionInstanceGuid() {
        return sessionInstanceGuid;
    }
    public void setSessionInstanceGuid(String sessionInstanceGuid) {
        this.sessionInstanceGuid = sessionInstanceGuid;
    }
    public String getTimeWindowGuid() {
        return timeWindowGuid;
    }
    public void setTimeWindowGuid(String timeWindowGuid) {
        this.timeWindowGuid = timeWindowGuid;
    }
    public SessionCompletionState getState() {
        return state;
    }
    public void setState(SessionCompletionState state) {
        this.state = state;
    }
    public Integer getEndDay() {
        return endDay;
    }
    public void setEndDay(Integer endDay) {
        this.endDay = endDay;
    }
    public LocalDate getEndDate() {
        return endDate;
    }
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
