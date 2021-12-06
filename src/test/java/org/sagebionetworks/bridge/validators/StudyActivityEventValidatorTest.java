package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.TestUtils.createEvent;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
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
        Validate.entityThrowingException(CREATE_INSTANCE, createBuilder().build());
        Validate.entityThrowingException(DELETE_INSTANCE, createBuilder().build());
    }
    
    @Test
    public void appIdNull() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withAppId(null);
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void appIdBlank() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withAppId(" ");
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "appId", CANNOT_BE_BLANK);
    }

    @Test
    public void userIdNull() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withUserId(null);
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "userId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void userIdBlank() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withUserId("");
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "userId", CANNOT_BE_BLANK);
    }

    @Test
    public void studyIdNull() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withStudyId(null);
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "studyId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void studyIdBlank() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withStudyId("\t");
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "studyId", CANNOT_BE_BLANK);
    }

    @Test
    public void eventIdNull() {
        StudyActivityEvent event = createEvent(null, MODIFIED_ON, null);
        assertValidatorMessage(CREATE_INSTANCE, event, "eventId", INVALID_EVENT_ID);
    }
    
    @Test
    public void eventIdBlank() {
        StudyActivityEvent event = createEvent("", MODIFIED_ON, null);
        assertValidatorMessage(CREATE_INSTANCE, event, "eventId", INVALID_EVENT_ID);
    }
    
    @Test
    public void timestampNull() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withTimestamp(null);
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "timestamp", CANNOT_BE_NULL);
    }

    @Test
    public void createdOnNull() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withCreatedOn(null);
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "createdOn", CANNOT_BE_NULL);
    }

    @Test
    public void clientTimeZoneInvalid() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withClientTimeZone("America/Europe");
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "clientTimeZone", TIME_ZONE_ERROR);
    }
    
    @Test
    public void clientTimeZoneNullOK() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withClientTimeZone(null);
        Validate.entityThrowingException(CREATE_INSTANCE, builder.build());
    }
    
    @Test
    public void deleteSkipsCertainFields() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withTimestamp(null);
        builder.withCreatedOn(null);
        builder.withClientTimeZone("America/Europe"); // we just ignore this
        Validate.entityThrowingException(DELETE_INSTANCE, builder.build());
    }
    
    @Test
    public void stringLengthValidation_answerValue() {
        StudyActivityEvent.Builder builder = createBuilder();
        builder.withAnswerValue(generateStringOfLength(256));
        assertValidatorMessage(CREATE_INSTANCE, builder.build(), "answerValue", getInvalidStringLengthMessage(255));
    }
    
    private StudyActivityEvent.Builder createBuilder() { 
        StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder();
        builder.withAppId(TEST_APP_ID);
        builder.withUserId(TEST_USER_ID);
        builder.withStudyId(TEST_STUDY_ID);
        builder.withObjectType(TIMELINE_RETRIEVED);
        builder.withTimestamp(MODIFIED_ON);
        builder.withAnswerValue("my answer");
        builder.withClientTimeZone("America/Los_Angeles");
        builder.withCreatedOn(TestConstants.CREATED_ON);
        return builder;
    }
    
}
