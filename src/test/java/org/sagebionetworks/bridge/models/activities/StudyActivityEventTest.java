package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * This object is never deserialized directly from an API request, but it is 
 * returned through the API, so the JSON it produces must be correct.
 */
public class StudyActivityEventTest {

    @Test
    public void test() throws Exception { 
        StudyActivityEvent event = new StudyActivityEvent();
        event.setAppId(TEST_APP_ID);
        event.setUserId(TEST_USER_ID);
        event.setEventId("eventKey");
        event.setTimestamp(MODIFIED_ON);
        event.setAnswerValue("my answer");
        event.setClientTimeZone("America/Los_Angeles");
        event.setCreatedOn(CREATED_ON);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(event);
        assertEquals(node.size(), 6);
        assertEquals(node.get("eventId").textValue(), "eventKey");
        assertEquals(node.get("timestamp").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("answerValue").textValue(), "my answer");
        assertEquals(node.get("clientTimeZone").textValue(), "America/Los_Angeles");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("type").textValue(), "ActivityEvent");
    }
    
}
