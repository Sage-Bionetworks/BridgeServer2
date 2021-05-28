package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.CREATE_INSTANCE;
import static org.sagebionetworks.bridge.validators.StudyActivityEventValidator.DELETE_INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.Validate.TIME_ZONE_ERROR;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

public class StudyActivityEventValidatorTest extends Mockito {

    @Test
    public void valid() {
        Validate.entityThrowingException(CREATE_INSTANCE, createEvent());
        Validate.entityThrowingException(DELETE_INSTANCE, createEvent());
    }
    
    @Test
    public void appIdNull() {
        StudyActivityEvent event = createEvent();
        event.setAppId(null);
        assertValidatorMessage(CREATE_INSTANCE, event, "appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void appIdBlank() {
        StudyActivityEvent event = createEvent();
        event.setAppId(" ");
        assertValidatorMessage(CREATE_INSTANCE, event, "appId", CANNOT_BE_BLANK);
    }

    @Test
    public void userIdNull() {
        StudyActivityEvent event = createEvent();
        event.setUserId(null);
        assertValidatorMessage(CREATE_INSTANCE, event, "userId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void userIdBlank() {
        StudyActivityEvent event = createEvent();
        event.setUserId("");
        assertValidatorMessage(CREATE_INSTANCE, event, "userId", CANNOT_BE_BLANK);
    }

    @Test
    public void studyIdNull() {
        StudyActivityEvent event = createEvent();
        event.setStudyId(null);
        assertValidatorMessage(CREATE_INSTANCE, event, "studyId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdBlank() {
        StudyActivityEvent event = createEvent();
        event.setStudyId("\t");
        assertValidatorMessage(CREATE_INSTANCE, event, "studyId", CANNOT_BE_BLANK);
    }

    @Test
    public void eventIdNull() {
        StudyActivityEvent event = createEvent();
        event.setEventId(null);
        assertValidatorMessage(CREATE_INSTANCE, event, "eventId", INVALID_EVENT_ID);
    }
    
    @Test
    public void eventIdBlank() {
        StudyActivityEvent event = createEvent();
        event.setEventId("");
        assertValidatorMessage(CREATE_INSTANCE, event, "eventId", INVALID_EVENT_ID);
    }
    
    @Test
    public void timestampNull() {
        StudyActivityEvent event = createEvent();
        event.setTimestamp(null);
        assertValidatorMessage(CREATE_INSTANCE, event, "timestamp", CANNOT_BE_NULL);
    }

    @Test
    public void createdOnNull() {
        StudyActivityEvent event = createEvent();
        event.setCreatedOn(null);
        assertValidatorMessage(CREATE_INSTANCE, event, "createdOn", CANNOT_BE_NULL);
    }

    @Test
    public void clientTimeZoneInvalid() {
        StudyActivityEvent event = createEvent();
        event.setClientTimeZone("America/Europe");
        assertValidatorMessage(CREATE_INSTANCE, event, "clientTimeZone", TIME_ZONE_ERROR);
    }
    
    @Test
    public void clientTimeZoneNullOK() {
        StudyActivityEvent event = createEvent();
        event.setClientTimeZone(null);
        Validate.entityThrowingException(CREATE_INSTANCE, event);
    }
    
    @Test
    public void deleteSkipsCertainFields() {
        StudyActivityEvent event = createEvent();
        event.setTimestamp(null);
        event.setCreatedOn(null);
        event.setClientTimeZone("America/Europe"); // we just ignore this
        Validate.entityThrowingException(DELETE_INSTANCE, createEvent());
    }
    
    private StudyActivityEvent createEvent() { 
        StudyActivityEvent event = new StudyActivityEvent();
        event.setAppId(TEST_APP_ID);
        event.setUserId(TEST_USER_ID);
        event.setStudyId(TEST_STUDY_ID);
        event.setEventId("timeline_retrieved");
        event.setTimestamp(MODIFIED_ON);
        event.setAnswerValue("my answer");
        event.setClientTimeZone("America/Los_Angeles");
        event.setCreatedOn(TestConstants.CREATED_ON);
        return event;
    }
    
}
