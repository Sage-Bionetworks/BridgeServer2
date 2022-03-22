package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.Objects;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;

public final class EventStreamWindow {
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
    @Override
    public int hashCode() {
        return Objects.hash(endDate, endDay, sessionInstanceGuid, state, timeWindowGuid);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EventStreamWindow other = (EventStreamWindow) obj;
        return Objects.equals(endDate, other.endDate) && Objects.equals(endDay, other.endDay)
                && Objects.equals(sessionInstanceGuid, other.sessionInstanceGuid) && state == other.state
                && Objects.equals(timeWindowGuid, other.timeWindowGuid);
    }
    
}
