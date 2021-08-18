package org.sagebionetworks.bridge.models.schedules2;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_2_GUID;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.sagebionetworks.bridge.models.schedules2.NotificationTest.MESSAGE;
import static org.sagebionetworks.bridge.models.schedules2.NotificationType.BEFORE_WINDOW_END;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.RANDOMIZED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class SessionTest {

    public static Session createValidSession() {
        Session session = new Session();
        session.setLabels(LABELS);
        session.setName("Do weekly survey");
        session.setGuid(SESSION_GUID_1);
        session.setStartEventIds(Lists.newArrayList("custom:activities_retrieved", "custom:timeline_retrieved"));
        session.setDelay(Period.parse("P1W"));
        session.setOccurrences(19);
        session.setInterval(Period.parse("P7D"));
        session.setPerformanceOrder(RANDOMIZED);
        
        TimeWindow window = new TimeWindow();
        window.setGuid(SESSION_WINDOW_GUID_1);
        window.setStartTime(LocalTime.parse("08:00"));
        window.setExpiration(Period.parse("PT6H"));
        window.setPersistent(true);
        session.setTimeWindows(ImmutableList.of(window));
        
        AssessmentReference asmt1 = new AssessmentReference();
        asmt1.setGuid(ASSESSMENT_1_GUID);
        asmt1.setAppId("local");
        asmt1.setIdentifier("Local Assessment 1");
        asmt1.setTitle("Assessment 1");
        asmt1.setMinutesToComplete(3);
        asmt1.setRevision(100);
        asmt1.setLabels(LABELS);
        
        AssessmentReference asmt2 = new AssessmentReference();
        asmt2.setGuid(ASSESSMENT_2_GUID);
        asmt2.setAppId("shared");
        asmt2.setIdentifier("Shared Assessment 2");
        asmt2.setTitle("Assessment 2");
        asmt2.setMinutesToComplete(5);
        asmt2.setRevision(200);
        asmt2.setLabels(LABELS);
        session.setAssessments(ImmutableList.of(asmt1, asmt2));
        
        NotificationMessage frMessage = new NotificationMessage.Builder()
                .withLang("fr").withSubject("le subject").withMessage("le msg").build();
        
        Notification notification = new Notification();
        notification.setNotifyAt(BEFORE_WINDOW_END);
        notification.setOffset(Period.parse("PT10M"));
        notification.setAllowSnooze(true);
        notification.setMessages(ImmutableList.of(MESSAGE, frMessage));
        session.setNotifications(ImmutableList.of(notification));
        
        return session;
    }

    @Test
    public void canSerialize() throws Exception {
        Session session = createValidSession();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(session);
        
        assertEquals(node.size(), 12);
        assertEquals(node.get("guid").textValue(), SESSION_GUID_1);
        assertEquals(node.get("name").textValue(), "Do weekly survey");
        assertEquals(node.get("startEventIds").get(0).textValue(), "custom:activities_retrieved");
        assertEquals(node.get("startEventIds").get(1).textValue(), "custom:timeline_retrieved");
        assertEquals(node.get("delay").textValue(), "P1W");
        assertEquals(node.get("occurrences").intValue(), 19);
        assertEquals(node.get("interval").textValue(), "P7D");
        assertEquals(node.get("performanceOrder").textValue(), "randomized");
        assertEquals(node.get("type").textValue(), "Session");
        
        ArrayNode notificationsArray = (ArrayNode)node.get("notifications");
        assertEquals(notificationsArray.size(), 1);
        JsonNode noteNode = notificationsArray.get(0);
        assertEquals(noteNode.get("notifyAt").textValue(), "before_window_end");
        assertEquals(noteNode.get("offset").textValue(), "PT10M");
        assertTrue(noteNode.get("allowSnooze").booleanValue());
        assertEquals(noteNode.get("type").textValue(), "Notification");
        
        ArrayNode messagesArray = (ArrayNode)noteNode.get("messages");
        assertEquals(messagesArray.size(), 2);
        assertEquals(messagesArray.get(0).size(), 4);
        assertEquals(messagesArray.get(0).get("lang").textValue(), "en");
        assertEquals(messagesArray.get(0).get("subject").textValue(), "subject");
        assertEquals(messagesArray.get(0).get("message").textValue(), "msg");
        assertEquals(messagesArray.get(0).get("type").textValue(), "NotificationMessage");
        
        // Testing one member of the array is enough; we test the serialization of
        // these objects as well.
        ArrayNode labelsArray = (ArrayNode)node.get("labels");
        assertEquals(labelsArray.size(), 2);
        assertEquals(labelsArray.get(0).size(), 3);
        assertEquals(labelsArray.get(0).get("lang").textValue(), "en");
        assertEquals(labelsArray.get(0).get("value").textValue(), "English");
        assertEquals(labelsArray.get(0).get("type").textValue(), "Label");
        
        ArrayNode asmtsArray = (ArrayNode)node.get("assessments");
        assertEquals(asmtsArray.size(), 2);
        assertEquals(asmtsArray.get(0).size(), 8);
        assertEquals(asmtsArray.get(0).get("guid").textValue(), ASSESSMENT_1_GUID);
        assertEquals(asmtsArray.get(0).get("appId").textValue(), "local");
        assertEquals(asmtsArray.get(0).get("identifier").textValue(), "Local Assessment 1");
        assertEquals(asmtsArray.get(0).get("title").textValue(), "Assessment 1");
        assertEquals(asmtsArray.get(0).get("minutesToComplete").intValue(), 3);
        assertEquals(asmtsArray.get(0).get("revision").intValue(), 100);
        assertEquals(asmtsArray.get(0).get("type").textValue(), "AssessmentReference");
        assertEquals(asmtsArray.get(0).get("labels").size(), 2);
        
        ArrayNode windowsArray = (ArrayNode)node.get("timeWindows");
        assertEquals(windowsArray.size(), 1);
        assertEquals(windowsArray.get(0).size(), 5);
        assertEquals(windowsArray.get(0).get("guid").textValue(), SESSION_WINDOW_GUID_1);
        assertEquals(windowsArray.get(0).get("startTime").textValue(), "08:00");
        assertEquals(windowsArray.get(0).get("expiration").textValue(), "PT6H");
        assertTrue(windowsArray.get(0).get("persistent").booleanValue());
        assertEquals(windowsArray.get(0).get("type").textValue(), "TimeWindow");
        
        Session deser = BridgeObjectMapper.get().readValue(node.toString(), Session.class);
        
        assertEquals(deser.getGuid(), SESSION_GUID_1);
        assertEquals(deser.getName(), "Do weekly survey");
        assertEquals(deser.getStartEventIds().get(0), "custom:activities_retrieved");
        assertEquals(deser.getStartEventIds().get(1), "custom:timeline_retrieved");
        assertEquals(deser.getDelay(), Period.parse("P1W"));
        assertEquals(deser.getOccurrences(), Integer.valueOf(19));
        assertEquals(deser.getInterval(), Period.parse("P7D"));
        assertEquals(deser.getPerformanceOrder(), PerformanceOrder.RANDOMIZED);
        
        assertEquals(deser.getLabels().size(), 2);
        List<Label> labels = deser.getLabels();
        assertEquals(labels.get(0).getLang(), "en");
        assertEquals(labels.get(0).getValue(), "English");
   
        assertEquals(deser.getAssessments().size(), 2);
        List<AssessmentReference> assessments = deser.getAssessments();
        assertEquals(assessments.get(0).getGuid(), ASSESSMENT_1_GUID);
        assertEquals(assessments.get(0).getTitle(), "Assessment 1");
        assertEquals(assessments.get(0).getMinutesToComplete(), Integer.valueOf(3));
        assertEquals(assessments.get(0).getAppId(), "local");
        
        assertEquals(deser.getTimeWindows().size(), 1);
        List<TimeWindow> windows = deser.getTimeWindows();
        assertEquals(windows.get(0).getGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(windows.get(0).getStartTime(), LocalTime.parse("08:00"));
        assertEquals(windows.get(0).getExpiration(), Period.parse("PT6H"));
        assertTrue(windows.get(0).isPersistent());
    }
    
    @Test
    public void collectionsSetToEmpty() {
        Session session = new Session();
        assertEquals(session.getAssessments(), ImmutableList.of());
        assertEquals(session.getLabels(), ImmutableList.of());
        assertEquals(session.getTimeWindows(), ImmutableList.of());
    }
    
    @Test
    public void testHibernateProperties() {
        Schedule2 schedule = new Schedule2();
        
        Session session = new Session();
        session.setSchedule(schedule);
        session.setPosition(10);
        
        assertEquals(session.getSchedule(), schedule);
        assertEquals(session.getPosition(), 10);
    }
}
