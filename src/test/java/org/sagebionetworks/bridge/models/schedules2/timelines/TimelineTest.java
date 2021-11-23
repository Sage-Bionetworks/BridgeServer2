package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_2_GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_3;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_4;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;

public class TimelineTest extends Mockito {

    @Test
    public void canSerialize() {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        schedule.setDuration(Period.parse("P3W"));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(timeline);
        assertNull(node.get("lang"));
        assertEquals(node.get("type").textValue(), "Timeline");
        
        assertEquals(node.get("schedule").size(), 8); // 4 doubled by a study burst design
        JsonNode schNode = node.get("schedule").get(0);
        assertEquals(schNode.get("refGuid").textValue(), SESSION_GUID_1);
        assertEquals(schNode.get("startEventId").textValue(), "activities_retrieved");
        assertEquals(schNode.get("instanceGuid").textValue(), "faQS0dRjAt9xNFTfOd5XqA");
        assertEquals(schNode.get("startDay").intValue(), 7);
        assertEquals(schNode.get("endDay").intValue(), 7);
        assertEquals(schNode.get("startTime").textValue(), "08:00");
        assertEquals(schNode.get("expiration").textValue(), "PT6H");
        assertTrue(schNode.get("persistent").booleanValue());
        assertNull(schNode.get("studyBurstId"));
        assertNull(schNode.get("studyBurstNum"));
        assertEquals(schNode.get("type").textValue(), "ScheduledSession");
        assertEquals(schNode.get("assessments")
                .get(0).get("instanceGuid").textValue(), "5NzDH5Q4V2VkSBFQF2HntA");
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
        assertEquals(sessNode.get("performanceOrder").textValue(), "randomized");
        assertEquals(sessNode.get("minutesToComplete").intValue(), 8);
        
        JsonNode msgNode = sessNode.get("notifications").get(0).get("message"); 
        assertEquals(msgNode.get("lang").textValue(), "en");
        assertEquals(msgNode.get("subject").textValue(), "subject");
        assertEquals(msgNode.get("message").textValue(), "msg");
        assertEquals(msgNode.get("type").textValue(), "NotificationMessage");
        
        assertEquals(sessNode.get("notifications").get(0).get("type").textValue(), "NotificationInfo");
        
        // This one is produced by a study burst
        schNode =  node.get("schedule").get(2);
        assertEquals(schNode.get("studyBurstId").textValue(), "burst1");
        assertEquals(schNode.get("studyBurstNum").intValue(), 1);

        // The timeline includes information about this studyburst
        JsonNode burstNode = node.get("studyBursts").get(0);
        assertEquals(burstNode.get("identifier").textValue(), "burst1");
        assertEquals(burstNode.get("interval").textValue(), "P1W");
        assertEquals(burstNode.get("occurrences").intValue(), 2);
        assertEquals(burstNode.get("type").textValue(), "StudyBurstInfo");
    }
    
    @Test
    public void generatesTimelineMetadataRecord() throws Exception {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        schedule.setDuration(Period.parse("P2W"));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();
        
        // This is a metadata record for a burst
        TimelineMetadata meta1 = metadata.get(6);
        
        assertEquals(meta1.getStudyBurstId(), "burst1");
        assertEquals(meta1.getStudyBurstNum(), Integer.valueOf(2));

        // This is the assessment #1 record
        TimelineMetadata meta2 = metadata.get(1);
        String sessionInstanceGuid = "faQS0dRjAt9xNFTfOd5XqA";
        String asmtInstanceGuid = "5NzDH5Q4V2VkSBFQF2HntA";
        assertEquals(meta2.getGuid(), asmtInstanceGuid);
        assertEquals(meta2.getAssessmentInstanceGuid(), asmtInstanceGuid);
        assertEquals(meta2.getAssessmentGuid(), ASSESSMENT_1_GUID);
        assertEquals(meta2.getAssessmentId(), "Local Assessment 1");
        assertEquals(meta2.getAssessmentRevision(), Integer.valueOf(100));
        assertEquals(meta2.getSessionInstanceGuid(), sessionInstanceGuid);
        assertEquals(meta2.getSessionGuid(), SESSION_GUID_1);
        assertEquals(meta2.getSessionStartEventId(), "activities_retrieved");
        assertEquals(meta2.getSessionInstanceStartDay(), Integer.valueOf(7));
        assertEquals(meta2.getSessionInstanceEndDay(), Integer.valueOf(7));
        assertEquals(meta2.getTimeWindowGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(meta2.getScheduleGuid(), SCHEDULE_GUID);
        assertEquals(meta2.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(meta2.isSchedulePublished());
        assertEquals(meta2.getAppId(), TEST_APP_ID);
        
        // This is the assessment #2 record
        TimelineMetadata meta3 = metadata.get(2);
        asmtInstanceGuid = "Sq-rdk91XHT6C8TnSUme1A";
        assertEquals(meta3.getGuid(), asmtInstanceGuid);
        assertEquals(meta3.getAssessmentInstanceGuid(), asmtInstanceGuid);
        assertEquals(meta3.getAssessmentGuid(), ASSESSMENT_2_GUID);
        assertEquals(meta3.getAssessmentId(), "Shared Assessment 2");
        assertEquals(meta3.getAssessmentRevision(), Integer.valueOf(200));
        assertEquals(meta3.getSessionInstanceGuid(), sessionInstanceGuid);
        assertEquals(meta3.getSessionGuid(), SESSION_GUID_1);
        assertEquals(meta3.getSessionStartEventId(), "activities_retrieved");
        assertEquals(meta3.getSessionInstanceStartDay(), Integer.valueOf(7));
        assertEquals(meta3.getSessionInstanceEndDay(), Integer.valueOf(7));
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
        
        assertEquals(node.size(), 7);
        assertEquals(node.get("assessments").size(), 0);
        assertEquals(node.get("sessions").size(), 0);
        assertEquals(node.get("schedule").size(), 0);
        assertEquals(node.get("studyBursts").size(), 0);
        assertEquals(node.get("totalMinutes").size(), 0);
        assertEquals(node.get("totalNotifications").size(), 0);
        assertEquals(node.get("type").textValue(), "Timeline");
    }
    
    @Test
    public void sessionInsertionOrderPreserved() { 
        Session sess1 = SessionTest.createValidSession();
        sess1.setGuid(SESSION_GUID_1);
        
        Session sess2 = SessionTest.createValidSession();
        sess2.setGuid(SESSION_GUID_2);
        
        Session sess3 = SessionTest.createValidSession();
        sess3.setGuid(SESSION_GUID_3);
        
        Session sess4 = SessionTest.createValidSession();
        sess4.setGuid(SESSION_GUID_4);
        
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        schedule.setSessions(ImmutableList.of(sess1, sess2, sess3, sess4));
        
        // This fails if you substitute a HashMap for the LinkedHashMap in 
        // Timeline, which preserves key insertion order.
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        assertEquals(timeline.getSessions().get(0).getGuid(), SESSION_GUID_1);
        assertEquals(timeline.getSessions().get(1).getGuid(), SESSION_GUID_2);
        assertEquals(timeline.getSessions().get(2).getGuid(), SESSION_GUID_3);
        assertEquals(timeline.getSessions().get(3).getGuid(), SESSION_GUID_4);
    }
    
    @Test
    public void calculatesRunningTotalsOfMinutesAndNotifications() throws Exception {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        // seven session with two events, each taking 8 minutes = 112 minutes, then
        // each of these is also triggered by a study burst that would occur 2 times, 
        // doubling the total to 224 minutes
        assertEquals(timeline.getTotalMinutes(), 224);
        
        // each session has one notification, 7 notifications, triggered twice by two
        // events, then doubled  by two study burst events.
        assertEquals(timeline.getTotalNotifications(), 28);
        
        // Alter the schedule so the notification repeats every 2 days within the 
        // time window of 7 days, with 4 events triggering it, so that is 1 
        // notification + 3 from the interval = 4 per window * 28 = 112 total notifications.
        schedule.getSessions().get(0).getNotifications().get(0).setInterval(Period.parse("P2D"));
        schedule.getSessions().get(0).getTimeWindows().get(0).setExpiration(Period.parse("P6D"));
        timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        assertEquals(timeline.getTotalNotifications(), 112);
    }
}
