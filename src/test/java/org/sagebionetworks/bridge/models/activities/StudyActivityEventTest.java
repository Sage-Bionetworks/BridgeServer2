package org.sagebionetworks.bridge.models.activities;

import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.QUESTION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.ANSWERED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.math.BigInteger;

import org.joda.time.Period;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * This object is never deserialized directly from an API request, but it is 
 * returned through the API, so the JSON it produces must be correct.
 */
public class StudyActivityEventTest {

    @Test
    public void test() throws Exception {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withEventId("eventKey")
                .withTimestamp(MODIFIED_ON)
                .withAnswerValue("my answer")
                .withClientTimeZone("America/Los_Angeles")
                .withCreatedOn(CREATED_ON)
                .withStudyBurstId("studyBurstId")
                .withOriginEventId("originEventId")
                .withPeriodFromOrigin(Period.parse("P3W"))
                .withRecordCount(10)
                .withUpdateType(FUTURE_ONLY).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(event);
        assertEquals(node.size(), 11);
        assertEquals(node.get("eventId").textValue(), "eventKey");
        assertEquals(node.get("timestamp").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("answerValue").textValue(), "my answer");
        assertEquals(node.get("clientTimeZone").textValue(), "America/Los_Angeles");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("recordCount").intValue(), 10);
        assertEquals(node.get("updateType").textValue(), "future_only");
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("originEventId").textValue(), "originEventId");
        assertEquals(node.get("periodFromOrigin").textValue(), "P3W");
        assertEquals(node.get("type").textValue(), "ActivityEvent");
    }
    
    @Test
    public void createsValidEvent() {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectType(QUESTION)
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withUpdateType(MUTABLE)
                .withOriginEventId("enrollment")
                .withStudyBurstId("foo")
                .withPeriodFromOrigin(Period.parse("P2D"))
                .withEventType(ANSWERED).build();
        
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getEventId(), "question:foo:answered=anAnswer");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getAnswerValue(), "anAnswer");
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getOriginEventId(), "enrollment");
        assertEquals(event.getStudyBurstId(), "foo");
        assertEquals(event.getPeriodFromOrigin(), Period.parse("P2D"));
        assertNull(event.getRecordCount());
        assertEquals(event.getUpdateType(), MUTABLE);
    }
    
    @Test
    public void builder_nullSafe() {
        StudyActivityEvent event = new StudyActivityEvent.Builder().build();
        assertNull(event.getEventId());
    }
    
    @Test
    public void builder_objectTypeIsNull() {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withEventType(ANSWERED).build();
        assertNull(event.getEventId());
    }
    
    @Test
    public void builder_updateTypeIsNull() {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectType(QUESTION)
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withEventType(ANSWERED).build();
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void recordify() throws Exception {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectType(QUESTION)
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withUpdateType(MUTABLE)
                .withOriginEventId("enrollment")
                .withStudyBurstId("foo")
                .withPeriodFromOrigin(Period.parse("P2D"))
                .withEventType(ANSWERED)
                .withRecordCount(7).build();
        
        Object[] record = StudyActivityEvent.recordify(event);
        assertEquals(record[0], TEST_APP_ID);
        assertEquals(record[1], TEST_USER_ID);
        assertEquals(record[2], TEST_STUDY_ID);
        assertEquals(record[3], "question:foo:answered=anAnswer");
        assertEquals(record[4], BigInteger.valueOf(MODIFIED_ON.getMillis()));
        assertEquals(record[5], "anAnswer");
        assertEquals(record[6], "America/Los_Angeles");
        assertEquals(record[7], BigInteger.valueOf(CREATED_ON.getMillis()));
        assertEquals(record[8], "foo");
        assertEquals(record[9], "enrollment");
        assertEquals(record[10], "P2D");
        assertEquals(record[11], BigInteger.valueOf(7));
    }
    
    @Test
    public void create() {
        Object[] record = new Object[12];
        record[0] = TEST_APP_ID;
        record[1] = TEST_USER_ID;
        record[2] = TEST_STUDY_ID;
        record[3] = "question:foo:answered=anAnswer";
        record[4] = BigInteger.valueOf(MODIFIED_ON.getMillis());
        record[5] = "anAnswer";
        record[6] = "America/Los_Angeles";
        record[7] = BigInteger.valueOf(CREATED_ON.getMillis());
        record[8] = "foo";
        record[9] = "enrollment";
        record[10] = "P2D";
        record[11] = BigInteger.valueOf(7);
        
        StudyActivityEvent event = StudyActivityEvent.create(record);
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getEventId(), "question:foo:answered=anAnswer");
        assertEquals(event.getTimestamp().withZone(UTC), MODIFIED_ON);
        assertEquals(event.getAnswerValue(), "anAnswer");
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(event.getCreatedOn().withZone(UTC), CREATED_ON);
        assertEquals(event.getStudyBurstId(), "foo");
        assertEquals(event.getOriginEventId(), "enrollment");
        assertEquals(event.getPeriodFromOrigin(), Period.parse("P2D"));
        assertEquals(event.getRecordCount(), Integer.valueOf(7));
    }
}
