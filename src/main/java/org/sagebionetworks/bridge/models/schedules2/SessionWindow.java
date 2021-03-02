package org.sagebionetworks.bridge.models.schedules2;

import javax.persistence.Convert;
import javax.persistence.Id;

import org.joda.time.LocalTime;
import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;

public class SessionWindow {
    
    @Id
    private String guid;
    private LocalTime startTime;
    @Convert(converter = PeriodToStringConverter.class)
    private Period expiresAfter;
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
    public Period getExpiresAfter() {
        return expiresAfter;
    }
    public void setExpiresAfter(Period expiresAfter) {
        this.expiresAfter = expiresAfter;
    }
    public boolean isPersistent() {
        return persistent;
    }
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }
}
