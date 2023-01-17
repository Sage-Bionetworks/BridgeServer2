package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.hibernate.LocalTimeToStringConverter;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;

import javax.persistence.Convert;

public final class EventStreamWindow {
    private String sessionInstanceGuid;
    private String timeWindowGuid;
    private SessionCompletionState state;
    private LocalDate startDate;
    @Convert(converter = LocalTimeToStringConverter.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    private Integer endDay;
    private LocalDate endDate;
    @Convert(converter = LocalTimeToStringConverter.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime endTime;
    
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
    public LocalDate getStartDate() {
        return startDate;
    }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    public LocalTime getStartTime() {
        return startTime;
    }
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }
    public Integer getEndDay() {
        return endDay;
    }
    public void setEndDay(Integer endDay) {
        this.endDay = endDay;
    }
    public LocalTime getEndTime() {
        return endTime;
    }
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
    public LocalDate getEndDate() {
        return endDate;
    }
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    @Override
    public int hashCode() {
        return Objects.hash(endDate, endDay, sessionInstanceGuid, state, timeWindowGuid, startDate, startTime, endTime);
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
                && Objects.equals(timeWindowGuid, other.timeWindowGuid) && Objects.equals(startDate, other.startDate)
                && Objects.equals(startTime, other.startTime) && Objects.equals(endTime, other.endTime);
    }
    
}
