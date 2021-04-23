package org.sagebionetworks.bridge.dao;

import java.util.Map;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public interface AdherenceRecordDao {
    
    AdherenceRecord get(AdherenceRecord record);

    void create(AdherenceRecord record);
    
    void update(AdherenceRecord record);
    
    PagedResourceList<AdherenceRecord> getAdherenceRecords(
            Map<String, DateTime> events, AdherenceRecordsSearch search);
}
