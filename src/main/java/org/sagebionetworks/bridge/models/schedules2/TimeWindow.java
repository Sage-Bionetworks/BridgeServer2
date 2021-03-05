package org.sagebionetworks.bridge.models.schedules2;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.LocalTimeToStringConverter;
import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;

@Embeddable
@Table(name = "ScheduleSessionTimeWindows")
public class TimeWindow {
    
    private String guid;
    @Convert(converter = LocalTimeToStringConverter.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime startTime;
    @Convert(converter = PeriodToStringConverter.class)
    @Column(name = "expirationPeriod")
    private Period expiration;
    private boolean persistent;

    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public LocalTime getStartTime() {
        return startTime;
    }
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }
    public Period getExpiration() {
        return expiration;
    }
    public void setExpiration(Period expiration) {
        this.expiration = expiration;
    }
    public boolean isPersistent() {
        return persistent;
    }
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }
    @Override
    public int hashCode() {
        return Objects.hash(expiration, guid, persistent, startTime);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        TimeWindow other = (TimeWindow) obj;
        return Objects.equals(expiration, other.expiration) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(persistent, other.persistent) &&
                Objects.equals(startTime, other.startTime);
    }
}
