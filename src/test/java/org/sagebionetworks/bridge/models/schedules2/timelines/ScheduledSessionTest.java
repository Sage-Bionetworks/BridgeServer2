package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class ScheduledSessionTest extends Mockito {

    @Test
    public void canSerialize() throws Exception {
        Session session = new Session();
        session.setGuid(SESSION_GUID_1);
        session.setStartEventIds(ImmutableList.of("enrollment"));
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_WINDOW_GUID_1);
        
        ScheduledAssessment asmt = new ScheduledAssessment("ref", "instanceGuid", null);
        
        ScheduledSession schSession = new ScheduledSession.Builder()
                .withSession(session)
                .withTimeWindow(window)
                .withStartEventId("timeline_retrieved")
                .withInstanceGuid("instanceGuid")
                .withStartDay(10)
                .withEndDay(13)
                .withDelayTime(Period.parse("PT3H"))
                .withStartTime(LocalTime.parse("17:00"))
                .withExpiration(Period.parse("PT30M"))
                .withPersistent(true)
                .withScheduledAssessment(asmt)
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schSession);
        assertEquals(node.get("refGuid").textValue(), SESSION_GUID_1);
        assertEquals(node.get("instanceGuid").textValue(), "instanceGuid");
        assertEquals(node.get("startEventId").textValue(), "timeline_retrieved");
        assertEquals(node.get("startDay").intValue(), 10);
        assertEquals(node.get("endDay").intValue(), 13);
        assertEquals(node.get("startTime").textValue(), "17:00");
        assertEquals(node.get("delayTime").textValue(), "PT3H");
        assertEquals(node.get("expiration").textValue(), "PT30M");
        assertEquals(node.get("timeWindowGuid").textValue(), SESSION_WINDOW_GUID_1);
        assertTrue(node.get("persistent").booleanValue());
        assertEquals(node.get("type").textValue(), "ScheduledSession");
        assertEquals(node.get("assessments").size(), 1);
        
        // Not part of the scheduled session JSON, but carried over to 
        // initialize the TimelineMetadata record without a lookup.
        assertEquals(schSession.getSession(), session);
        assertEquals(schSession.getTimeWindow(), window);
    }
    
    @Test
    public void serializationHandlesNulls() {
        ScheduledSession schSession = new ScheduledSession.Builder()
                .withSession(new Session()).withTimeWindow(new TimeWindow())
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schSession);
        assertEquals(node.size(), 4);
        assertEquals(node.get("startDay").intValue(), 0);
        assertEquals(node.get("endDay").intValue(), 0);
        assertEquals(node.get("assessments").size(), 0);
        assertEquals(node.get("type").textValue(), "ScheduledSession");
    }
    
    @Test
    public void copyWithoutAssessments() {
        Session session = new Session();
        session.setGuid(SESSION_GUID_1);
        session.setStartEventIds(ImmutableList.of("enrollment"));
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_WINDOW_GUID_1);
        
        ScheduledAssessment asmt = new ScheduledAssessment("ref", "instanceGuid", null);
        
        ScheduledSession.Builder builder = new ScheduledSession.Builder()
                .withSession(session)
                .withTimeWindow(window)
                .withStartEventId("timeline_retrieved")
                .withInstanceGuid("instanceGuid")
                .withStartDay(10)
                .withEndDay(13)
                .withDelayTime(Period.parse("PT3H"))
                .withStartTime(LocalTime.parse("17:00"))
                .withExpiration(Period.parse("PT30M"))
                .withPersistent(true)
                .withScheduledAssessment(asmt);
        
        ScheduledSession.Builder copy = builder.copyWithoutAssessments();
        ScheduledSession schSession = copy.build();
        
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getInstanceGuid(), "instanceGuid");
        assertEquals(schSession.getStartEventId(), "timeline_retrieved");
        assertEquals(schSession.getStartDay(), 10);
        assertEquals(schSession.getEndDay(), 13);
        assertEquals(schSession.getStartTime(), LocalTime.parse("17:00"));
        assertEquals(schSession.getDelayTime(), Period.parse("PT3H"));
        assertEquals(schSession.getExpiration(), Period.parse("PT30M"));
        assertEquals(schSession.getTimeWindow(), window);
        assertTrue(schSession.isPersistent());
        assertTrue(schSession.getAssessments().isEmpty());
    }
}
