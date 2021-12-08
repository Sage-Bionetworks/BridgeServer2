package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class EventStreamAdherenceReportTest {
    
    @Test
    public void canSerialize() throws Exception {
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        report.setActiveOnly(true);
        report.setTimestamp(CREATED_ON);
        report.setAdherencePercent(56);
        report.setStreams(ImmutableList.of(new EventStream(), new EventStream()));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        assertTrue(node.get("activeOnly").booleanValue());
        assertEquals(node.get("timestamp").textValue(), CREATED_ON.toString());
        assertEquals(node.get("adherencePercent").intValue(), 56);
        assertEquals(node.get("streams").size(), 2);
        assertEquals(node.get("type").textValue(), "EventStreamAdherenceReport");
        
        EventStreamAdherenceReport deser = BridgeObjectMapper.get().readValue(node.toString(), EventStreamAdherenceReport.class);
        assertTrue(deser.isActiveOnly());
        assertEquals(deser.getTimestamp(), CREATED_ON);
        assertEquals(deser.getAdherencePercent(), 56);
        assertEquals(deser.getStreams().size(), 2);
    }

}
