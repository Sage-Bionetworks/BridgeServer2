package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public interface AdherenceRecordDao {
    
    void updateAdherenceRecords(List<AdherenceRecord> recordList);

    PagedResourceList<AdherenceRecord> getAdherenceRecords(AdherenceRecordsSearch search);

}
