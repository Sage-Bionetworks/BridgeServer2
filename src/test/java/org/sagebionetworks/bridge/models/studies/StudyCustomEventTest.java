package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StudyCustomEventTest {

    @Test
    public void canSerialize() throws Exception { 
        StudyCustomEvent event = new StudyCustomEvent("anEvent", IMMUTABLE); 

        JsonNode node = BridgeObjectMapper.get().valueToTree(event);
        assertEquals(node.size(), 3);
        assertEquals(node.get("eventId").textValue(), "anEvent");
        assertEquals(node.get("updateType").textValue(), "immutable");
        assertEquals(node.get("type").textValue(), "CustomEvent");
        
        StudyCustomEvent deser = BridgeObjectMapper.get().readValue(node.toString(), StudyCustomEvent.class);
        assertEquals(deser.getEventId(), "anEvent");
        assertEquals(deser.getUpdateType(), IMMUTABLE);
    }
}
