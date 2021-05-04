package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AdherenceRecordListTest {

    @Test
    public void canSerialize() throws Exception {
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord(GUID);
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord(GUID+"2");
        AdherenceRecordList list = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals(node.get("records").size(), 2);
        // just verify these are adherence records, which we test separately
        assertEquals(node.get("records").get(0).get("clientTimeZone").textValue(), "America/Los_Angeles");
        assertEquals(node.get("records").get(1).get("clientTimeZone").textValue(), "America/Los_Angeles");
        assertEquals(node.get("type").textValue(), "AdherenceRecordList");
        
        AdherenceRecordList deser = BridgeObjectMapper.get().readValue(node.toString(), AdherenceRecordList.class);
        assertEquals(deser.getRecords().size(), 2);
        assertEquals(deser.getRecords().get(0).getClientTimeZone(), "America/Los_Angeles");
        assertEquals(deser.getRecords().get(1).getClientTimeZone(), "America/Los_Angeles");
    }
    
}
