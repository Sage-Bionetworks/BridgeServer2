package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.models.BridgeEntity;

public class AdherenceRecordList implements BridgeEntity {
    private final List<AdherenceRecord> records;
    
    @JsonCreator
    public AdherenceRecordList(@JsonProperty("records") List<AdherenceRecord> records) {
        this.records = records;
    }
    public List<AdherenceRecord> getRecords() { 
        return (records == null) ? ImmutableList.of() : records;
    }
}
