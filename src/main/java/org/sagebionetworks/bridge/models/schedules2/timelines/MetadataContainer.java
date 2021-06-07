package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.services.Schedule2Service;

/**
 * When working with adherence records, you frequently need the TimelineMetadata
 * record, and carrying this between methods on the callstack is tedious. So this
 * class provides access to metadata as well as being a parameter object.
 */
public class MetadataContainer {
    Schedule2Service scheduleService;
    Map<String, TimelineMetadata> metadata = new HashMap<>();
    Map<String, AdherenceRecord> records = new HashMap<>();
    Map<String, AdherenceRecord> sessionUpdates = new HashMap<>();
    List<AdherenceRecord> assessmentUpdates = new ArrayList<>();
    
    public MetadataContainer(Schedule2Service scheduleService, List<AdherenceRecord> records) {
        this.scheduleService = scheduleService;
        for (AdherenceRecord record : records) {
            addRecord(record);
        }
    }
    
    public void addRecord(AdherenceRecord record) {
        this.records.put(record.getInstanceGuid(), record);
        TimelineMetadata meta = scheduleService.getTimelineMetadata(record.getInstanceGuid()).orElse(null);
        if (meta != null) {
            metadata.put(meta.getGuid(), meta);
            // Persistent activities can be done more than once, and are only differentiated by their
            // start times, while other activities can only be done once in a time stream, so we use
            // the startedOn timestamp for persistent records and the eventTimestamp for other records. 
            // This is set on all updates and is not exposed through the API.
            if (meta.isTimeWindowPersistent()) {
                record.setInstanceTimestamp(record.getStartedOn());
            } else {
                record.setInstanceTimestamp(record.getEventTimestamp());    
            }
            if (meta.getAssessmentInstanceGuid() == null) {
                sessionUpdates.put(meta.getSessionInstanceGuid(), record);
            } else {
                assessmentUpdates.add(record);
            }
        }
    }
    
    public Collection<AdherenceRecord> getAssessments() {
        return assessmentUpdates;
    }
    
    public Collection<AdherenceRecord> getSessionUpdates() {
        return sessionUpdates.values();
    }
    
    public AdherenceRecord getRecord(String instanceGuid) {
        return records.get(instanceGuid);
    }
    
    public TimelineMetadata getMetadata(String instanceGuid) {
        TimelineMetadata tm = metadata.get(instanceGuid);
        if (tm == null) {
            tm = scheduleService.getTimelineMetadata(instanceGuid).orElse(null);
        }
        return tm;
    }
}


