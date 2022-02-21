package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;

public class SessionInfoTest extends Mockito {

    // This also tests SessionInfo.createTimelineEntry constructor
    @Test
    public void canSerialize() throws Exception {
        Session session = SessionTest.createValidSession();
        
        SessionInfo info = SessionInfo.createTimelineEntry(session);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("guid").textValue(), SESSION_GUID_1);
        assertEquals(node.get("label").textValue(), "English");
        assertEquals(node.get("symbol").textValue(), "✯");
        assertEquals(node.get("performanceOrder").textValue(), "randomized");
        assertEquals(node.get("timeWindowGuids").get(0).textValue(), SESSION_WINDOW_GUID_1);
        // this combines the minutes from two assessments, correctly
        assertEquals(node.get("minutesToComplete").intValue(), 8);
        
        JsonNode noteNode = node.get("notifications").get(0);
        assertEquals(noteNode.get("message").get("lang").textValue(), "en");
        assertEquals(noteNode.get("message").get("subject").textValue(), "subject");
        assertEquals(noteNode.get("message").get("message").textValue(), "msg");
        assertEquals(noteNode.get("message").get("type").textValue(), "NotificationMessage");
        
        assertEquals(node.get("type").textValue(), "SessionInfo");
    }
    
    @Test
    public void createScheduleEntry() {
        Session session = SessionTest.createValidSession();
        SessionInfo info = SessionInfo.createTimelineEntry(session);
        
        SessionInfo min = SessionInfo.createScheduleEntry(info);
        assertEquals(min.getGuid(), session.getGuid());
        assertEquals(min.getLabel(), info.getLabel());
        assertEquals(min.getPerformanceOrder(), info.getPerformanceOrder());
        assertFalse(min.getNotifications().isEmpty());
        assertEquals(min.getMinutesToComplete(), info.getMinutesToComplete());
        assertNull(min.getSymbol());
        assertNull(min.getStartEventId());
        assertNull(min.getTimeWindowGuids());
    }
    
    @Test
    public void noMinutesAddsUpToNoProperty() {
        Session session = SessionTest.createValidSession();
        session.getAssessments().get(0).setMinutesToComplete(null);
        session.getAssessments().get(1).setMinutesToComplete(null);
        
        SessionInfo info = SessionInfo.createTimelineEntry(session);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertNull(node.get("minutesToComplete"));
    }

    @Test
    public void mixedMinutesAddsWorks() {
        Session session = SessionTest.createValidSession();
        session.getAssessments().get(0).setMinutesToComplete(null);
        
        SessionInfo info = SessionInfo.createTimelineEntry(session);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("minutesToComplete").intValue(), 5);
    }

    @Test
    public void serializationHandlesNulls() {
        SessionInfo info = SessionInfo.createTimelineEntry(new Session());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("timeWindowGuids").size(), 0);
        assertEquals(node.get("notifications").size(), 0);
        assertEquals(node.size(), 3);
        assertEquals(node.get("type").textValue(), "SessionInfo");
    }
}
