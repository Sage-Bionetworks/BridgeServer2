package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class TimelineMetadataViewTest {
    
    @Test
    public void canSerialize() {
        TimelineMetadataView view = new TimelineMetadataView(
                TimelineMetadataTest.createTimelineMetadata());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(view);
        assertEquals(node.size(), 2);
        
        JsonNode meta = node.get("metadata");
        assertEquals(meta.get("sessionInstanceStartDay").textValue(), "5");
        assertEquals(meta.get("sessionInstanceEndDay").textValue(), "15");
        assertEquals(meta.get("sessionInstanceGuid").textValue(), "sessionInstanceGuid");
        assertEquals(meta.get("assessmentInstanceGuid").textValue(), "assessmentInstanceGuid");
        assertEquals(meta.get("timeWindowPersistent").textValue(), "true");
        assertEquals(meta.get("timeWindowGuid").textValue(), "FFFFFFFFFFFFFFFFFFFFFFFF");
        assertEquals(meta.get("studyBurstNum").textValue(), "4");
        assertEquals(meta.get("schedulePublished").textValue(), "true");
        assertEquals(meta.get("assessmentGuid").textValue(), "111111111111111111111111");
        assertEquals(meta.get("assessmentRevision").textValue(), "7");
        assertEquals(meta.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(meta.get("sessionStartEventId").textValue(), "enrollment");
        assertEquals(meta.get("scheduleModifiedOn").textValue(), "2015-01-27T01:38:32.486Z");
        assertEquals(meta.get("appId").textValue(), "appId");
        assertEquals(meta.get("guid").textValue(), "BBBBBBBBBBBBBBBBBBBBBBBB");
        assertEquals(meta.get("sessionGuid").textValue(), "sessionGuid");
        assertEquals(meta.get("assessmentId").textValue(), "assessmentId");
        assertEquals(meta.get("scheduleGuid").textValue(), "scheduleGuid");

        assertEquals(node.get("type").textValue(), "TimelineMetadata");
    }

    @Test
    public void canSerialize_empty() {
        TimelineMetadataView view = new TimelineMetadataView(new TimelineMetadata());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(view);
        assertEquals(node.size(), 2);
        assertEquals(node.get("metadata").size(), 0);
    }
}
