package org.sagebionetworks.bridge.models.schedules2;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public class SessionId implements Serializable {

    @Column(name = "scheduleGuid")
    private String scheduleGuid;

    @Column(name = "guid")
    private String guid;

    public SessionId() {
    }
 
    public SessionId(String scheduleGuid, String guid) {
        this.scheduleGuid = scheduleGuid;
        this.guid = guid;
    }
    
    public String getScheduleGuid() {
        return scheduleGuid;
    }
    
    public String getGuid() {
        return guid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleGuid, guid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SessionId other = (SessionId) obj;
        return Objects.equals(scheduleGuid, other.scheduleGuid) && Objects.equals(guid, other.guid);
    }
    
}
