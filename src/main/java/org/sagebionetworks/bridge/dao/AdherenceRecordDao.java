package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public interface AdherenceRecordDao {
    
    void updateAdherenceRecord(AdherenceRecord record);
    
    PagedResourceList<AdherenceRecord> getAdherenceRecords(AdherenceRecordsSearch search);

    void deleteAdherenceRecordPermanently(AdherenceRecord record);

}
