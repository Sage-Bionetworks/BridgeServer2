package org.sagebionetworks.bridge.models.schedules2.timelines;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;

public class SessionState {
    DateTime earliest;
    DateTime latest;
    int total;
    int started;
    int finished;
    
    public SessionState(int total) {
        this.total = total;
    }
    boolean isUnstarted() {
        return started == 0;
    }
    boolean isFinished() {
        return finished == total;
    }
    public void add(AdherenceRecord record) {
        if (record.getStartedOn() != null) {
            started++;
        }
        if (record.getFinishedOn() != null) {
            finished++;
        }
        replaceEarliest(record.getStartedOn());
        replaceLatest(record.getFinishedOn());
    }
    private void replaceEarliest(DateTime current) {
        if (current != null && (earliest == null || current.isBefore(earliest))) {
            earliest = current;
        }
    }
    private void replaceLatest(DateTime current) {
        if (current != null && (latest == null || current.isAfter(latest))) {
            latest = current;
        }
    }
    public boolean updateSessionRecord(AdherenceRecord sessionRecord) {
        boolean updated = false;
        if (isUnstarted()) {
            if (sessionRecord.getStartedOn() != null) {
                sessionRecord.setStartedOn(null);
                updated = true;
            }
            if (sessionRecord.getFinishedOn() != null) {
                sessionRecord.setFinishedOn(null);
                updated = true;
            }
        } else if (isFinished()) {
            if (sessionRecord.getStartedOn() == null) {
                sessionRecord.setStartedOn(earliest);
                updated = true;
            }
            if (sessionRecord.getFinishedOn() == null) { 
                sessionRecord.setFinishedOn(latest);
                updated = true;
            }
        } else {
            if (sessionRecord.getStartedOn() == null) {
                sessionRecord.setStartedOn(earliest);
                updated = true;
            }
            // “unfinish” this record, if need be
            if (sessionRecord.getFinishedOn() != null) {
                sessionRecord.setFinishedOn(null);
                updated = true;
            }
        }
        return updated;
    }
}
