package org.sagebionetworks.bridge.models.activities;

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
            new DynamoActivityEvent.Builder().build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("timestamp").get(0), "timestamp cannot be null");
            assertEquals(e.getErrors().get("healthCode").get(0), "healthCode cannot be null or blank");
        }
        try {
            new DynamoActivityEvent.Builder().withHealthCode("BBB").build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("timestamp").get(0), "timestamp cannot be null");
        }
        try {
            new DynamoActivityEvent.Builder().withObjectType(ActivityEventObjectType.QUESTION).build();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("timestamp").get(0), "timestamp cannot be null");
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
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"healthCode\":\"test-health-code\",\n" +
                "   \"eventId\":\"test-event\",\n" +
                "   \"answerValue\":\"dummy answer\",\n" +
                "   \"timestamp\":\"2018-08-20T16:15:19.913Z\"\n" +
                "}";

        // Convert to POJO.
        ActivityEvent activityEvent = BridgeObjectMapper.get().readValue(jsonText, ActivityEvent.class);
        assertEquals(activityEvent.getHealthCode(), "test-health-code");
        assertEquals(activityEvent.getEventId(), "test-event");
        assertEquals(activityEvent.getAnswerValue(), "dummy answer");
        assertEquals(activityEvent.getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-08-20T16:15:19.913Z"));

        // Convert back to JSON.
        JsonNode activityNode = BridgeObjectMapper.get().convertValue(activityEvent, JsonNode.class);
        assertEquals(activityNode.get("healthCode").textValue(), "test-health-code");
        assertEquals(activityNode.get("eventId").textValue(), "test-event");
        assertEquals(activityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(activityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(activityNode.get("type").textValue(), "ActivityEvent");

        // Test activity event writer, which filters out health code.
        String filteredJsonText = ActivityEvent.ACTIVITY_EVENT_WRITER.writeValueAsString(activityEvent);
        JsonNode filteredActivityNode = BridgeObjectMapper.get().readTree(filteredJsonText);
        assertNull(filteredActivityNode.get("healthCode"));
        assertEquals(filteredActivityNode.get("eventId").textValue(), "test-event");
        assertEquals(filteredActivityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(filteredActivityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(filteredActivityNode.get("type").textValue(), "ActivityEvent");
    }
}
