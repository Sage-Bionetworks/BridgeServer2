package org.sagebionetworks.bridge.models.schedules2.timelines;

import org.joda.time.Period;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;

public class StudyBurstInfo {

    private String identifier;
    private String originEventId;
    private Period delay;
    private Period interval;
    private Integer occurrences;
    
    public static final StudyBurstInfo create(StudyBurst burst) {
        StudyBurstInfo info = new StudyBurstInfo();
        info.identifier = burst.getIdentifier();
        info.originEventId = burst.getOriginEventId();
        info.delay = burst.getDelay();
        info.interval = burst.getInterval();
        info.occurrences = burst.getOccurrences();
        return info;
    }

    public String getIdentifier() {
        return identifier;
    }
    public String getOriginEventId() { 
        return originEventId;
    }
    public Period getDelay() {
        return delay;
    }
    public Period getInterval() {
        return interval;
    }
    public Integer getOccurrences() {
        return occurrences;
    }
}
