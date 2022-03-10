package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class AdherenceStatisticsTest {
    
    @Test
    public void canSerialize() throws Exception {
        AdherenceStatisticsEntry entry = new AdherenceStatisticsEntry();
        
        AdherenceStatistics stats = new AdherenceStatistics();
        stats.setAdherenceThresholdPercentage(50);
        stats.setCompliant(100);
        stats.setNoncompliant(80);
        stats.setTotalActive(180);
        stats.setEntries(ImmutableList.of(entry));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(stats);
        
        assertEquals(node.size(), 6);
        assertEquals(node.get("adherenceThresholdPercentage").intValue(), 50);
        assertEquals(node.get("compliant").intValue(), 100);
        assertEquals(node.get("noncompliant").intValue(), 80);
        assertEquals(node.get("totalActive").intValue(), 180);
        assertEquals(node.get("entries").size(), 1);
        assertEquals(node.get("type").textValue(), "AdherenceStatistics");
    }

}
