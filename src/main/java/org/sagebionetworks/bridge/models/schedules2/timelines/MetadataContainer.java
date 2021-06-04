package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.services.Schedule2Service;

public class MetadataContainer {
    Schedule2Service scheduleService;
    Map<String, TimelineMetadata> metadata = new HashMap<>();
    Map<String, AdherenceRecord> records = new HashMap<>();
    Map<String, AdherenceRecord> sessionUpdates = new HashMap<>();
    List<AdherenceRecord> assessmentUpdates = new ArrayList<>();
    
    public MetadataContainer(Schedule2Service scheduleService, List<AdherenceRecord> records) {
        this.scheduleService = scheduleService;
        addRecords(records);
    }
    
    private void addRecords(List<AdherenceRecord> records) {
        for (AdherenceRecord record : records) {
            this.records.put(record.getInstanceGuid(), record);
            TimelineMetadata meta = scheduleService.getTimelineMetadata(record.getInstanceGuid()).orElse(null);
            if (meta != null) {
                metadata.put(meta.getGuid(), meta);
                if (meta.getAssessmentInstanceGuid() == null) {
                    sessionUpdates.put(meta.getSessionInstanceGuid(), record);
                } else {
                    assessmentUpdates.add(record);
                }
            }
        }
    }
    
    public void addSession(AdherenceRecord session) {
        sessionUpdates.put(session.getInstanceGuid(), session);
    }
    
    public Collection<AdherenceRecord> getAssessments() {
        return assessmentUpdates;
    }
    
    public Collection<AdherenceRecord> getSessions() {
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


