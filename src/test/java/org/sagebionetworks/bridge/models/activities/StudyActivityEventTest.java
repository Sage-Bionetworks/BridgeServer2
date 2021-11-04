package org.sagebionetworks.bridge.models.activities;

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
                .withRecordCount(10)
                .withUpdateType(FUTURE_ONLY).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(event);
        assertEquals(node.size(), 10);
        assertEquals(node.get("eventId").textValue(), "eventKey");
        assertEquals(node.get("timestamp").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("answerValue").textValue(), "my answer");
        assertEquals(node.get("clientTimeZone").textValue(), "America/Los_Angeles");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("recordCount").intValue(), 10);
        assertEquals(node.get("updateType").textValue(), "future_only");
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("originEventId").textValue(), "originEventId");
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
                .withEventType(ANSWERED).build();
        
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getEventId(), "question:foo:answered=anAnswer");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getAnswerValue(), "anAnswer");
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getRecordCount(), 0);
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
}
