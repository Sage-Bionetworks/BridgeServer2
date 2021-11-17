package org.sagebionetworks.bridge.models.schedules2.timelines;

import org.joda.time.Period;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;

public class StudyBurstInfo {

    private String identifier;
    private Period interval;
    private Integer occurrences;
    
    public static final StudyBurstInfo create(StudyBurst burst) {
        StudyBurstInfo info = new StudyBurstInfo();
        info.identifier = burst.getIdentifier();
        info.interval = burst.getInterval();
        info.occurrences = burst.getOccurrences();
        return info;
    }

    public String getIdentifier() {
        return identifier;
    }
    public Period getInterval() {
        return interval;
    }
    public Integer getOccurrences() {
        return occurrences;
    }
}
