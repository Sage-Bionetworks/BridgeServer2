package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_2_GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;

public class TimelineTest extends Mockito {

    @Test
    public void canSerialize() {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        schedule.setDuration(Period.parse("P3W"));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(timeline);
        assertNull(node.get("lang"));
        assertEquals(node.get("type").textValue(), "Timeline");
        
        assertEquals(node.get("schedule").size(), 2);
        JsonNode schNode = node.get("schedule").get(0);
        assertEquals(schNode.get("refGuid").textValue(), SESSION_GUID_1);
        assertEquals(schNode.get("instanceGuid").textValue(), "So3SXnQm0sIt9vVIqj814Q");
        assertEquals(schNode.get("startDay").intValue(), 7);
        assertEquals(schNode.get("endDay").intValue(), 13);
        assertEquals(schNode.get("startTime").textValue(), "08:00");
        assertEquals(schNode.get("expiration").textValue(), "P6D");
        assertTrue(schNode.get("persistent").booleanValue());
        assertEquals(schNode.get("type").textValue(), "ScheduledSession");
        assertEquals(schNode.get("assessments")
                .get(0).get("instanceGuid").textValue(), "ZBi2x9clKyYLrPcHNqpjmA");
        assertEquals(schNode.get("assessments")
                .get(0).get("refKey").textValue(), "646f8c04646f8c04");
        assertEquals(schNode.get("assessments")
                .get(0).get("type").textValue(), "ScheduledAssessment");
        
        assertEquals(node.get("assessments").size(), 2);
        JsonNode asmtNode = node.get("assessments").get(0);
        assertEquals(asmtNode.get("guid").textValue(), ASSESSMENT_1_GUID);
        assertEquals(asmtNode.get("appId").textValue(), "local");
        assertEquals(asmtNode.get("label").textValue(), "English");
        assertEquals(asmtNode.get("minutesToComplete").intValue(), 3);
        assertEquals(asmtNode.get("key").textValue(), "646f8c04646f8c04");
        assertEquals(asmtNode.get("revision").intValue(), 100);
        assertEquals(asmtNode.get("type").textValue(), "AssessmentInfo");

        assertEquals(node.get("sessions").size(), 1);
        JsonNode sessNode = node.get("sessions").get(0);
        assertEquals(sessNode.get("guid").textValue(), SESSION_GUID_1);
        assertEquals(sessNode.get("label").textValue(), "English");
        assertEquals(sessNode.get("startEventId").textValue(), "activities_retrieved");
        assertEquals(sessNode.get("performanceOrder").textValue(), "randomized");
        assertEquals(sessNode.get("notifyAt").textValue(), "start_of_window");
        assertEquals(sessNode.get("remindAt").textValue(), "before_window_end");
        assertEquals(sessNode.get("reminderPeriod").textValue(), "PT10M");
        assertTrue(sessNode.get("allowSnooze").booleanValue());
        assertEquals(sessNode.get("minutesToComplete").intValue(), 8);
        assertEquals(sessNode.get("message").get("lang").textValue(), "en");
        assertEquals(sessNode.get("message").get("subject").textValue(), "English");
        assertEquals(sessNode.get("message").get("message").textValue(), "Body");
        assertEquals(sessNode.get("message").get("type").textValue(), "NotificationMessage");
        assertEquals(sessNode.get("type").textValue(), "SessionInfo");
    }
    
    @Test
    public void generatesTimelineMetadataRecord() {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        schedule.setDuration(Period.parse("P2W"));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();
        
        // This is the session record
        TimelineMetadata meta1 = metadata.get(0);
        String sessionInstanceGuid = "So3SXnQm0sIt9vVIqj814Q";
        assertEquals(meta1.getGuid(), sessionInstanceGuid);
        assertNull(meta1.getAssessmentInstanceGuid());
        assertNull(meta1.getAssessmentGuid());
        assertNull(meta1.getAssessmentId());
        assertNull(meta1.getAssessmentRevision());
        assertEquals(meta1.getSessionInstanceGuid(), sessionInstanceGuid);
        assertEquals(meta1.getSessionGuid(), SESSION_GUID_1);
        assertEquals(meta1.getSessionStartEventId(), "activities_retrieved");
        assertEquals(meta1.getTimeWindowGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(meta1.getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(meta1.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(meta1.isSchedulePublished());
        assertEquals(meta1.getAppId(), TEST_APP_ID);

        // This is the assessment #1 record
        TimelineMetadata meta2 = metadata.get(1);
        String asmtInstanceGuid = "ZBi2x9clKyYLrPcHNqpjmA";
        assertEquals(meta2.getGuid(), asmtInstanceGuid);
        assertEquals(meta2.getAssessmentInstanceGuid(), asmtInstanceGuid);
        assertEquals(meta2.getAssessmentGuid(), ASSESSMENT_1_GUID);
        assertEquals(meta2.getAssessmentId(), "Local Assessment 1");
        assertEquals(meta2.getAssessmentRevision(), Integer.valueOf(100));
        assertEquals(meta2.getSessionInstanceGuid(), sessionInstanceGuid);
        assertEquals(meta2.getSessionGuid(), SESSION_GUID_1);
        assertEquals(meta2.getSessionStartEventId(), "activities_retrieved");
        assertEquals(meta2.getTimeWindowGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(meta2.getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(meta2.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(meta2.isSchedulePublished());
        assertEquals(meta2.getAppId(), TEST_APP_ID);
        
        // This is the assessment #2 record
        TimelineMetadata meta3 = metadata.get(2);
        asmtInstanceGuid = "b20vr-Bb2Om655sLmp5MjQ";
        assertEquals(meta3.getGuid(), asmtInstanceGuid);
        assertEquals(meta3.getAssessmentInstanceGuid(), asmtInstanceGuid);
        assertEquals(meta3.getAssessmentGuid(), ASSESSMENT_2_GUID);
        assertEquals(meta3.getAssessmentId(), "Shared Assessment 2");
        assertEquals(meta3.getAssessmentRevision(), Integer.valueOf(200));
        assertEquals(meta3.getSessionInstanceGuid(), sessionInstanceGuid);
        assertEquals(meta3.getSessionGuid(), SESSION_GUID_1);
        assertEquals(meta3.getSessionStartEventId(), "activities_retrieved");
        assertEquals(meta3.getTimeWindowGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(meta3.getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(meta3.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(meta3.isSchedulePublished());
        assertEquals(meta3.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void serializationHandlesNulls() {
        Timeline timeline = new Timeline.Builder().build();
        JsonNode node = BridgeObjectMapper.get().valueToTree(timeline);
        
        assertEquals(node.size(), 4);
        assertEquals(node.get("assessments").size(), 0);
        assertEquals(node.get("sessions").size(), 0);
        assertEquals(node.get("schedule").size(), 0);
        assertEquals(node.get("type").textValue(), "Timeline");
    }
}
