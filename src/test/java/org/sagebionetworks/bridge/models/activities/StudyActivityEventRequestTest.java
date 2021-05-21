package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SESSION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SURVEY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StudyActivityEventRequestTest {
    
    @Test
    public void testConstruction() {
        StudyActivityEventRequest request = createRequest();
        assertRequest(request);
    }

    @Test
    public void testActivityEventConstruction() {
        StudyActivityEventRequest request = createRequest();
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getAnswerValue(), "my answer");
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getEventId(), "survey:surveyGuid:finished");
    }
    
    @Test
    public void canDeserialize() throws Exception {
        // Try it with eventKey
        String json = TestUtils.createJson("{'eventKey':'event1',"+
                "'timestamp':'"+MODIFIED_ON.toString()+"',"+
                "'answerValue':'my answer',"+
                "'clientTimeZone':'America/Los_Angeles'}");
        
        StudyActivityEventRequest request = BridgeObjectMapper.get().readValue(json, StudyActivityEventRequest.class);
        assertEquals(request.getTimestamp(), MODIFIED_ON);
        assertEquals(request.getAnswerValue(), "my answer");
        assertEquals(request.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(request.getObjectId(), "event1");
        
        // Try it with eventId
        json = TestUtils.createJson("{'eventId':'event1',"+
                "'timestamp':'"+MODIFIED_ON.toString()+"',"+
                "'answerValue':'my answer',"+
                "'clientTimeZone':'America/Los_Angeles'}");
        
        request = BridgeObjectMapper.get().readValue(json, StudyActivityEventRequest.class);
        assertEquals(request.getTimestamp(), MODIFIED_ON);
        assertEquals(request.getAnswerValue(), "my answer");
        assertEquals(request.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(request.getObjectId(), "event1");
    }
    
    @Test
    public void copy() {
        StudyActivityEventRequest request = createRequest();
        
        StudyActivityEventRequest copy = request.copy();
        assertRequest(copy);
    }
    
    @Test
    public void defaults() {
        StudyActivityEventRequest request = new StudyActivityEventRequest();
        assertEquals(request.getObjectType(), CUSTOM);
        assertEquals(request.getUpdateType(), IMMUTABLE);
        
        StudyActivityEvent event = request.toStudyActivityEvent();
        assertNull(event.getAppId());
        assertNull(event.getUserId());
        assertNull(event.getStudyId());
        assertNull(event.getTimestamp());
        assertNull(event.getAnswerValue());
        assertNull(event.getClientTimeZone());
        assertNull(event.getCreatedOn());
        assertNull(event.getEventId());
    }
    
    @Test
    public void customStrippedFromObjectId() {
        StudyActivityEventRequest request = new StudyActivityEventRequest();
        request.objectId("CUSTOM:FOO");
        assertEquals(request.getObjectId(), "FOO");

        request.objectId("Custom:FOO");
        assertEquals(request.getObjectId(), "FOO");

        request.objectId("custom:FOO");
        assertEquals(request.getObjectId(), "FOO");
    }
    
    @Test
    public void objectTypeSetsUpdateType() {
        StudyActivityEventRequest request = new StudyActivityEventRequest()
                .objectType(SESSION);
        
        assertEquals(request.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void customObjectTypeSetsUpdateTypeFromMapping() {
        StudyActivityEventRequest request = createRequest()
                .objectType(CUSTOM)
                .objectId("event1");
        
        assertEquals(request.getUpdateType(), MUTABLE);
    }
    
    @Test
    public void customObjectStripOutInvalidCustomEvents() {
        StudyActivityEventRequest request = createRequest()
                .objectType(CUSTOM)
                .objectId("event2");
        
        assertNull(request.getObjectId());
    }
    
    private StudyActivityEventRequest createRequest() {
        Map<String,ActivityEventUpdateType> customEvents = ImmutableMap.of("event1", MUTABLE);
        return new StudyActivityEventRequest()
                .appId(TEST_APP_ID)
                .userId(TEST_USER_ID)
                .studyId(TEST_STUDY_ID)
                .timestamp(MODIFIED_ON)
                .answerValue("my answer")
                .clientTimeZone("America/Los_Angeles")
                .createdOn(CREATED_ON)
                .objectType(SURVEY)
                .objectId("surveyGuid")
                .eventType(FINISHED)
                .updateType(FUTURE_ONLY)
                .customEvents(customEvents);
    }

    private void assertRequest(StudyActivityEventRequest request) {
        assertEquals(request.getAppId(), TEST_APP_ID);
        assertEquals(request.getUserId(), TEST_USER_ID);
        assertEquals(request.getStudyId(), TEST_STUDY_ID);
        assertEquals(request.getTimestamp(), MODIFIED_ON);
        assertEquals(request.getAnswerValue(), "my answer");
        assertEquals(request.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(request.getCreatedOn(), CREATED_ON);
        assertEquals(request.getObjectType(), SURVEY);
        assertEquals(request.getObjectId(), "surveyGuid");
        assertEquals(request.getEventType(), FINISHED);
        assertEquals(request.getUpdateType(), FUTURE_ONLY);
    }
}
