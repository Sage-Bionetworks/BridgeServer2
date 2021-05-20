package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;

public class SessionInfoTest extends Mockito {

    @Test
    public void canSerialize() throws Exception {
        Session session = SessionTest.createValidSession();
        
        SessionInfo info = SessionInfo.create(session);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("guid").textValue(), SESSION_GUID_1);
        assertEquals(node.get("label").textValue(), "English");
        assertEquals(node.get("startEventId").textValue(), "activities_retrieved");
        assertEquals(node.get("performanceOrder").textValue(), "randomized");
        assertEquals(node.get("notifyAt").textValue(), "start_of_window");
        assertEquals(node.get("remindAt").textValue(), "before_window_end");
        assertEquals(node.get("timeWindowGuids").get(0).textValue(), SESSION_WINDOW_GUID_1);
        assertTrue(node.get("allowSnooze").booleanValue());
        // this combines the minutes from two assessments, correctly
        assertEquals(node.get("minutesToComplete").intValue(), 8);
        assertEquals(node.get("message").get("lang").textValue(), "en");
        assertEquals(node.get("message").get("subject").textValue(), "English");
        assertEquals(node.get("message").get("message").textValue(), "Body");
        assertEquals(node.get("message").get("type").textValue(), "NotificationMessage");
        assertEquals(node.get("reminderPeriod").textValue(), "PT10M");
        assertEquals(node.get("type").textValue(), "SessionInfo");
    }
    
    @Test
    public void noMinutesAddsUpToNoProperty() {
        Session session = SessionTest.createValidSession();
        session.getAssessments().get(0).setMinutesToComplete(null);
        session.getAssessments().get(1).setMinutesToComplete(null);
        
        SessionInfo info = SessionInfo.create(session);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertNull(node.get("minutesToComplete"));
    }

    @Test
    public void mixedMinutesAddsWorks() {
        Session session = SessionTest.createValidSession();
        session.getAssessments().get(0).setMinutesToComplete(null);
        
        SessionInfo info = SessionInfo.create(session);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("minutesToComplete").intValue(), 5);
    }

    @Test
    public void serializationHandlesNulls() {
        SessionInfo info = SessionInfo.create(new Session());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("timeWindowGuids").size(), 0);
        assertEquals(node.size(), 2);
        assertEquals(node.get("type").textValue(), "SessionInfo");
    }
}
