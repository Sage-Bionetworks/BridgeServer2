package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
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
        assertEquals(2, node.get("records").size());
        // just verify these are adherence records, which we test separately
        assertEquals("AdherenceRecord", node.get("records").get(0).get("type").textValue());;
        assertEquals("AdherenceRecord", node.get("records").get(1).get("type").textValue());;
        assertEquals(node.get("type").textValue(), "AdherenceRecordList");
        
        AdherenceRecordList deser = BridgeObjectMapper.get().readValue(node.toString(), AdherenceRecordList.class);
        assertEquals(2, deser.getRecords().size());
        assertEquals(TEST_STUDY_ID, deser.getRecords().get(0).getStudyId());
        assertEquals(TEST_STUDY_ID, deser.getRecords().get(1).getStudyId());
    }
    
}
