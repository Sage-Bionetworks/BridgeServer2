package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class AdherenceStatisticsEntryTest {
    
    @Test
    public void canSerialize() throws Exception {
        AdherenceStatisticsEntry entry = new AdherenceStatisticsEntry();
        entry.setLabel("label");
        entry.setSearchableLabel("searchableLabel");
        entry.setSessionName("sessionName");
        entry.setStudyBurstId("studyBurstId");
        entry.setStudyBurstNum(2);
        entry.setTotalActive(10);
        entry.setWeekInStudy(3);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(entry);
        assertEquals(node.size(), 8);
        assertEquals(node.get("label").textValue(), "label");
        assertEquals(node.get("searchableLabel").textValue(), "searchableLabel");
        assertEquals(node.get("sessionName").textValue(), "sessionName");
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("studyBurstNum").intValue(), 2);
        assertEquals(node.get("totalActive").intValue(), 10);
        assertEquals(node.get("weekInStudy").intValue(), 3);
        assertEquals(node.get("type").textValue(), "AdherenceStatisticsEntry");
        
        
    }

}
