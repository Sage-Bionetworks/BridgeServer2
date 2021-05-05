package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public interface AdherenceRecordDao {
    
    void updateAdherenceRecords(AdherenceRecordList recordList);

    PagedResourceList<AdherenceRecord> getAdherenceRecords(AdherenceRecordsSearch search);

}
