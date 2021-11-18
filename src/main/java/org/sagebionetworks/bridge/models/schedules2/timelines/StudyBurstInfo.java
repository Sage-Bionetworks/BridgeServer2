package org.sagebionetworks.bridge.models.schedules2.timelines;

import org.joda.time.Period;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;

public class StudyBurstInfo {

    private String identifier;
    private Period delay;
    private Period interval;
    private Integer occurrences;
    
    public static final StudyBurstInfo create(StudyBurst burst) {
        StudyBurstInfo info = new StudyBurstInfo();
        info.delay = burst.getDelay();
        info.identifier = burst.getIdentifier();
        info.interval = burst.getInterval();
        info.occurrences = burst.getOccurrences();
        return info;
    }

    public String getIdentifier() {
        return identifier;
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
