package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
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
        
        ScheduledAssessment asmt = new ScheduledAssessment.Builder()
                .withRefKey("ref")
                .withInstanceGuid("instanceGuid").build();
        
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
                .withStartDate(CREATED_ON.toLocalDate())
                .withEndDate(MODIFIED_ON.toLocalDate())
                .withState(ABANDONED)
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
        assertEquals(node.get("startDate").textValue(), CREATED_ON.toLocalDate().toString());
        assertEquals(node.get("endDate").textValue(), MODIFIED_ON.toLocalDate().toString());
        assertEquals(node.get("state").textValue(), "abandoned");
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
        assertEquals(node.size(), 2);
        assertNull(node.get("startDay"));
        assertNull(node.get("endDay"));
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
        
        ScheduledAssessment asmt = new ScheduledAssessment.Builder()
                .withRefKey("ref")
                .withInstanceGuid("instanceGuid").build();
        
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
        
        ScheduledSession.Builder copy = builder.build().toBuilder();
        ScheduledSession schSession = copy.build();
        
        assertEquals(schSession.getRefGuid(), SESSION_GUID_1);
        assertEquals(schSession.getInstanceGuid(), "instanceGuid");
        assertEquals(schSession.getStartEventId(), "timeline_retrieved");
        assertEquals(schSession.getStartDay(), Integer.valueOf(10));
        assertEquals(schSession.getEndDay(), Integer.valueOf(13));
        assertEquals(schSession.getStartTime(), LocalTime.parse("17:00"));
        assertEquals(schSession.getDelayTime(), Period.parse("PT3H"));
        assertEquals(schSession.getExpiration(), Period.parse("PT30M"));
        assertEquals(schSession.getTimeWindow(), window);
        assertTrue(schSession.isPersistent());
        assertTrue(schSession.getAssessments().isEmpty());
        assertNull(schSession.getStudyBurstId());
        assertNull(schSession.getStudyBurstNum());
    }
    
    @Test
    public void reportsStudyBurstInformation() {
        Session session = new Session();
        session.setGuid(SESSION_GUID_1);
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_WINDOW_GUID_1);
        
        ScheduledSession schSession = new ScheduledSession.Builder()
                .withSession(session)
                .withTimeWindow(window)
                .withStartEventId("study_burst:foo:01")
                .build();
        
        assertEquals(schSession.getStudyBurstId(), "foo");
        assertEquals(schSession.getStudyBurstNum(), Integer.valueOf(1));
    }
}
