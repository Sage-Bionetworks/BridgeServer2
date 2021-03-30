package org.sagebionetworks.bridge.models.schedules2;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.sagebionetworks.bridge.TestConstants.MESSAGES;
import static org.sagebionetworks.bridge.models.schedules2.NotificationType.START_OF_WINDOW;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.RANDOMIZED;
import static org.sagebionetworks.bridge.models.schedules2.ReminderType.BEFORE_WINDOW_END;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class SessionTest {

    public static Session createValidSession() {
        Session session = new Session();
        session.setLabels(LABELS);
        session.setName("Do weekly survey");
        session.setGuid("BBBBBBBB");
        session.setStartEventId("activities_retrieved");
        session.setDelay(Period.parse("P1W"));
        session.setOccurrences(19);
        session.setInterval(Period.parse("P7D"));
        session.setPerformanceOrder(RANDOMIZED);
        session.setNotifyAt(START_OF_WINDOW);
        session.setRemindAt(BEFORE_WINDOW_END);
        session.setReminderPeriod(Period.parse("PT10M"));
        session.setAllowSnooze(true);
        
        TimeWindow window = new TimeWindow();
        window.setGuid("CCCCCCCC");
        window.setStartTime(LocalTime.parse("08:00"));
        window.setExpiration(Period.parse("P6D"));
        window.setPersistent(true);
        session.setTimeWindows(ImmutableList.of(window));
        
        AssessmentReference asmt1 = new AssessmentReference();
        asmt1.setGuid("asmtRef1Guid");
        asmt1.setAppId("local");
        asmt1.setTitle("Assessment 1");
        asmt1.setMinutesToComplete(3);
        asmt1.setLabels(LABELS);
        
        AssessmentReference asmt2 = new AssessmentReference();
        asmt2.setGuid("asmtRef2Guid");
        asmt2.setAppId("shared");
        asmt2.setTitle("Assessment 2");
        asmt2.setMinutesToComplete(5);
        asmt2.setLabels(LABELS);
        session.setAssessments(ImmutableList.of(asmt1, asmt2));
        session.setMessages(MESSAGES);
        
        return session;
    }

    @Test
    public void canSerialize() throws Exception {
        Session session = createValidSession();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(session);
        
        assertEquals(node.size(), 16);
        assertEquals(node.get("guid").textValue(), "BBBBBBBB");
        assertEquals(node.get("name").textValue(), "Do weekly survey");
        assertEquals(node.get("startEventId").textValue(), "activities_retrieved");
        assertEquals(node.get("delay").textValue(), "P1W");
        assertEquals(node.get("occurrences").intValue(), 19);
        assertEquals(node.get("interval").textValue(), "P7D");
        assertEquals(node.get("performanceOrder").textValue(), "randomized");
        assertEquals(node.get("notifyAt").textValue(), "start_of_window");
        assertEquals(node.get("remindAt").textValue(), "before_window_end");
        assertEquals(node.get("reminderPeriod").textValue(), "PT10M");
        assertTrue(node.get("allowSnooze").booleanValue());
        assertEquals(node.get("type").textValue(), "Session");
        
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
        assertEquals(asmtsArray.get(0).size(), 6);
        assertEquals(asmtsArray.get(0).get("guid").textValue(), "asmtRef1Guid");
        assertEquals(asmtsArray.get(0).get("appId").textValue(), "local");
        assertEquals(asmtsArray.get(0).get("title").textValue(), "Assessment 1");
        assertEquals(asmtsArray.get(0).get("minutesToComplete").intValue(), 3);
        assertEquals(asmtsArray.get(0).get("type").textValue(), "AssessmentReference");
        assertEquals(asmtsArray.get(0).get("labels").size(), 2);
        
        ArrayNode windowsArray = (ArrayNode)node.get("timeWindows");
        assertEquals(windowsArray.size(), 1);
        assertEquals(windowsArray.get(0).size(), 5);
        assertEquals(windowsArray.get(0).get("guid").textValue(), "CCCCCCCC");
        assertEquals(windowsArray.get(0).get("startTime").textValue(), "08:00");
        assertEquals(windowsArray.get(0).get("expiration").textValue(), "P6D");
        assertTrue(windowsArray.get(0).get("persistent").booleanValue());
        assertEquals(windowsArray.get(0).get("type").textValue(), "TimeWindow");
        
        ArrayNode messagesArray = (ArrayNode)node.get("messages");
        assertEquals(messagesArray.size(), 2);
        assertEquals(messagesArray.get(0).size(), 4);
        assertEquals(messagesArray.get(0).get("lang").textValue(), "en");
        assertEquals(messagesArray.get(0).get("subject").textValue(), "English");
        assertEquals(messagesArray.get(0).get("message").textValue(), "Body");
        assertEquals(messagesArray.get(0).get("type").textValue(), "NotificationMessage");
        
        Session deser = BridgeObjectMapper.get().readValue(node.toString(), Session.class);
        
        assertEquals(deser.getGuid(), "BBBBBBBB");
        assertEquals(deser.getName(), "Do weekly survey");
        assertEquals(deser.getStartEventId(), "activities_retrieved");
        assertEquals(deser.getDelay(), Period.parse("P1W"));
        assertEquals(deser.getOccurrences(), Integer.valueOf(19));
        assertEquals(deser.getInterval(), Period.parse("P7D"));
        assertEquals(deser.getPerformanceOrder(), PerformanceOrder.RANDOMIZED);
        assertEquals(deser.getNotifyAt(), NotificationType.START_OF_WINDOW);
        assertEquals(deser.getRemindAt(), ReminderType.BEFORE_WINDOW_END);
        assertEquals(deser.getReminderPeriod(), Period.parse("PT10M"));
        assertTrue(deser.isAllowSnooze());
        
        assertEquals(deser.getLabels().size(), 2);
        List<Label> labels = deser.getLabels();
        assertEquals(labels.get(0).getLang(), "en");
        assertEquals(labels.get(0).getValue(), "English");
   
        assertEquals(deser.getAssessments().size(), 2);
        List<AssessmentReference> assessments = deser.getAssessments();
        assertEquals(assessments.get(0).getGuid(), "asmtRef1Guid");
        assertEquals(assessments.get(0).getTitle(), "Assessment 1");
        assertEquals(assessments.get(0).getMinutesToComplete(), Integer.valueOf(3));
        assertEquals(assessments.get(0).getAppId(), "local");
        
        assertEquals(deser.getTimeWindows().size(), 1);
        List<TimeWindow> windows = deser.getTimeWindows();
        assertEquals(windows.get(0).getGuid(), "CCCCCCCC");
        assertEquals(windows.get(0).getStartTime(), LocalTime.parse("08:00"));
        assertEquals(windows.get(0).getExpiration(), Period.parse("P6D"));
        assertTrue(windows.get(0).isPersistent());

        assertEquals(deser.getMessages().size(), 2);
        List<NotificationMessage> messages = deser.getMessages();
        assertEquals(messages.get(0).getLang(), "en");
        assertEquals(messages.get(0).getSubject(), "English");
        assertEquals(messages.get(0).getMessage(), "Body");
    }
    
    @Test
    public void collectionsSetToEmpty() {
        Session session = new Session();
        assertEquals(session.getAssessments(), ImmutableList.of());
        assertEquals(session.getLabels(), ImmutableList.of());
        assertEquals(session.getTimeWindows(), ImmutableList.of());
        assertEquals(session.getMessages(), ImmutableList.of());
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
