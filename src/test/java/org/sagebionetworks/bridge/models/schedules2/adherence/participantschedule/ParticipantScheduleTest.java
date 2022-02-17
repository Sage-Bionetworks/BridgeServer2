package org.sagebionetworks.bridge.models.schedules2.adherence.participantschedule;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.testng.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class ParticipantScheduleTest extends Mockito {
    
    
    @Test
    public void canSerialize() {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        ScheduledSession schSession = timeline.getSchedule().get(0);
        schSession.getTimeWindow().setGuid(null);
        
        schSession = schSession.toBuilder()
            .withStartDay(null)
            .withEndDay(null)
            .withStartEventId(null)
            .withStartDate(LocalDate.parse("2022-01-10"))
            .withEndDate(LocalDate.parse("2022-01-13"))
            .withState(SessionCompletionState.ABANDONED).build();
        
        DateRange range = new DateRange(CREATED_ON.toLocalDate(), MODIFIED_ON.toLocalDate());
        
        ParticipantSchedule participantSchedule = new ParticipantSchedule();
        participantSchedule.setDateRange(range);
        participantSchedule.setCreatedOn(CREATED_ON);
        participantSchedule.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        participantSchedule.setSchedule(ImmutableList.of(schSession));
        participantSchedule.setAssessments(timeline.getAssessments());
        participantSchedule.setSessions(timeline.getSessions());
        participantSchedule.setStudyBursts(timeline.getStudyBursts());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(participantSchedule);
        assertEquals(node.size(), 8);
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("clientTimeZone").textValue(), TEST_CLIENT_TIME_ZONE);
        assertEquals(node.get("dateRange").get("startDate").textValue(), CREATED_ON.toLocalDate().toString());
        assertEquals(node.get("dateRange").get("endDate").textValue(), MODIFIED_ON.toLocalDate().toString());
        assertEquals(node.get("type").textValue(), "ParticipantSchedule");
        
        JsonNode schNode = node.get("schedule").get(0);
        assertEquals(schNode.get("startDate").textValue(), "2022-01-10");
        assertEquals(schNode.get("endDate").textValue(), "2022-01-13");
        assertEquals(schNode.get("state").textValue(), "abandoned");
        // This is completely tested in ScheduledSessionTest
        
        JsonNode sessionNode = node.get("sessions").get(0);
        assertEquals(sessionNode.get("guid").textValue(), "BBBBBBBBBBBBBBBBBBBBBBBB");
        assertEquals(sessionNode.get("performanceOrder").textValue(), "randomized");
        // This is completely tested in SessionInfoTest
        
        JsonNode asmtNode = node.get("assessments").get(0);
        assertEquals(asmtNode.get("guid").textValue(), ASSESSMENT_1_GUID);
        // This is completely tested in AssessmentInfoTest
        
        JsonNode sbNode = node.get("studyBursts").get(0);
        assertEquals(sbNode.get("identifier").textValue(), "burst1");
        assertEquals(sbNode.get("originEventId").textValue(), "timeline_retrieved");
        assertEquals(sbNode.get("interval").textValue(), "P1W");
        assertEquals(sbNode.get("occurrences").intValue(), 2);
        assertEquals(sbNode.get("type").textValue(), "StudyBurstInfo");
        
        
    }
}
