package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITIES_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;

public class ActivityEventTest {

    @Test
    public void cannotConstructBadActivityEvent() {
        try {
            new DynamoActivityEvent.Builder().withObjectType(ACTIVITIES_RETRIEVED).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("healthCode").get(0), "healthCode cannot be null or blank");
        }
        try {
            new DynamoActivityEvent.Builder().withObjectType(ActivityEventObjectType.QUESTION).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("healthCode").get(0), "healthCode cannot be null or blank");
        }
        try {
            new DynamoActivityEvent.Builder().withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("healthCode").get(0), "healthCode cannot be null or blank");
        }
        try {
            new DynamoActivityEvent.Builder().withHealthCode("BBB").withTimestamp(DateTime.now()).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("eventId").get(0),
                    "eventId cannot be null (may be missing object or event type)");
        }
        
    }
    
    @Test
    public void canConstructSimpleEventId() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoActivityEvent.Builder();
        ActivityEvent event = builder.withObjectType(ActivityEventObjectType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "enrollment");
    }
    
    @Test
    public void canConstructEventNoAction() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoActivityEvent.Builder();
        ActivityEvent event = builder.withObjectType(ActivityEventObjectType.ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "enrollment");
    }
    
    @Test
    public void simpleActivityEventIdIsCorrect() {
        DateTime now = DateTime.now();
        ActivityEvent event = new DynamoActivityEvent.Builder().withHealthCode("BBB")
                .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "enrollment");
    }
    
    @Test
    public void activitiesRetrievedEvent() {
        DateTime now = DateTime.now();
        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withObjectType(ActivityEventObjectType.ACTIVITIES_RETRIEVED).withHealthCode("BBB").withTimestamp(now)
                .build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "activities_retrieved");
    }
    
    @Test
    public void testBuilder() {
        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withHealthCode("healthCode")
                .withStudyId("studyId")
                .withTimestamp(123L)
                .withObjectType(CUSTOM)
                .withObjectId("objectId")
                .withEventType(FINISHED)
                .withAnswerValue("true").build();
        
        assertEquals(event.getHealthCode(), "healthCode:studyId");
        assertEquals(event.getStudyId(), "studyId");
        assertEquals(event.getTimestamp(), Long.valueOf(123L));
        assertEquals(event.getEventId(), "custom:objectId:finished");
        assertEquals(event.getAnswerValue(), "true");

        event = new DynamoActivityEvent.Builder()
                .withHealthCode("healthCode")
                .withTimestamp(123L)
                .withObjectType(CUSTOM)
                .withObjectId("objectId")
                .withEventType(FINISHED)
                .withAnswerValue("true").build();
        
        assertEquals(event.getHealthCode(), "healthCode");
    }

    @Test
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"healthCode\":\"test-health-code\",\n" +
                "   \"studyId\":\"test-study\",\n" +
                "   \"eventId\":\"test-event\",\n" +
                "   \"answerValue\":\"dummy answer\",\n" +
                "   \"timestamp\":\"2018-08-20T16:15:19.913Z\"\n" +
                "}";

        // Convert to POJO.
        ActivityEvent activityEvent = BridgeObjectMapper.get().readValue(jsonText, ActivityEvent.class);
        assertNull(activityEvent.getHealthCode());
        assertEquals(activityEvent.getStudyId(), "test-study");
        assertEquals(activityEvent.getEventId(), "test-event");
        assertEquals(activityEvent.getAnswerValue(), "dummy answer");
        assertEquals(activityEvent.getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-08-20T16:15:19.913Z"));

        // Convert back to JSON.
        JsonNode activityNode = BridgeObjectMapper.get().valueToTree(activityEvent);
        assertNull(activityNode.get("healthCode"));
        assertEquals(activityNode.get("studyId").textValue(), "test-study");
        assertEquals(activityNode.get("eventId").textValue(), "test-event");
        assertEquals(activityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(activityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(activityNode.get("type").textValue(), "ActivityEvent");

        // Test that activity event does not include health code
        String filteredJsonText = BridgeObjectMapper.get().writeValueAsString(activityEvent);
        JsonNode filteredActivityNode = BridgeObjectMapper.get().readTree(filteredJsonText);
        assertNull(filteredActivityNode.get("healthCode"));
        assertEquals(filteredActivityNode.get("studyId").textValue(), "test-study");
        assertEquals(filteredActivityNode.get("eventId").textValue(), "test-event");
        assertEquals(filteredActivityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(filteredActivityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(filteredActivityNode.get("type").textValue(), "ActivityEvent");
        
        // without a studyId
        jsonText = "{\n" +
                "   \"healthCode\":\"test-health-code\",\n" +
                "   \"eventId\":\"test-event\",\n" +
                "   \"answerValue\":\"dummy answer\",\n" +
                "   \"timestamp\":\"2018-08-20T16:15:19.913Z\"\n" +
                "}";
        
        activityEvent = BridgeObjectMapper.get().readValue(jsonText, ActivityEvent.class);
        assertNull(activityEvent.getHealthCode());
        assertNull(activityEvent.getStudyId());
        assertEquals(activityEvent.getEventId(), "test-event");
        assertEquals(activityEvent.getAnswerValue(), "dummy answer");
        assertEquals(activityEvent.getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-08-20T16:15:19.913Z"));

        // Convert back to JSON.
        activityNode = BridgeObjectMapper.get().valueToTree(activityEvent);
        assertNull(activityNode.get("healthCode"));
        assertNull(activityNode.get("studyId"));
        assertEquals(activityNode.get("eventId").textValue(), "test-event");
        assertEquals(activityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(activityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(activityNode.get("type").textValue(), "ActivityEvent");
        
        filteredJsonText = BridgeObjectMapper.get().writeValueAsString(activityEvent);
        filteredActivityNode = BridgeObjectMapper.get().readTree(filteredJsonText);
        assertNull(filteredActivityNode.get("healthCode"));
    }
    
    @Test
    public void studyScopedHealthCodeWorks() {
        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withObjectType(CUSTOM)
                .withObjectId("test")
                .withHealthCode("AAA").build();
        assertEquals(event.getHealthCode(), "AAA");

        event = new DynamoActivityEvent.Builder()
                .withObjectType(CUSTOM)
                .withObjectId("test")
                .withHealthCode("AAA")
                .withStudyId("BBB").build();
        assertEquals(event.getHealthCode(), "AAA:BBB");
    }
}
