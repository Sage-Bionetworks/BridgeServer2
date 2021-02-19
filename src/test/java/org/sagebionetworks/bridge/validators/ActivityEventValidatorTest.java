package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.ANSWERED;
import static org.sagebionetworks.bridge.validators.ActivityEventValidator.ANSWER_VALUE_ERROR;
import static org.sagebionetworks.bridge.validators.ActivityEventValidator.EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.validators.ActivityEventValidator.INSTANCE;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;

public class ActivityEventValidatorTest {
    
    // It's easier to start with a fully-constructed object, and alter for a test
    private DynamoActivityEvent.Builder getEvent() {
        return new DynamoActivityEvent.Builder()
                .withHealthCode(HEALTH_CODE)
                .withStudyId(TEST_STUDY_ID)
                .withTimestamp(CREATED_ON)
                .withObjectType(CUSTOM)
                .withObjectId("fooboo")
                .withEventType(ANSWERED) // irrelevant here
                .withAnswerValue("anAnswer");
    }
    
    @Test
    public void validates() {
        Validate.entityThrowingException(INSTANCE, getEvent().build());
    }
    
    @Test
    public void eventIdNull() {
        ActivityEvent event = getEvent()
                .withObjectType(null)
                .withObjectId(null)
                .withEventType(null)
                .withAnswerValue(null)
                .build();
        assertValidatorMessage(INSTANCE, event, "eventId", EVENT_ID_ERROR);
    }

    public void answerValueRequired() {
        ActivityEvent event = getEvent().withEventType(ActivityEventType.ANSWERED)
                .withAnswerValue(null).build();
        assertValidatorMessage(INSTANCE, event, "answerValue", ANSWER_VALUE_ERROR);
    }
    
    @Test
    public void timestampNull() {
        ActivityEvent event = getEvent().withTimestamp((DateTime)null).build();
        assertValidatorMessage(INSTANCE, event, "timestamp", Validate.CANNOT_BE_NULL);
    }

    @Test
    public void healthCodeNull() {
        ActivityEvent event = getEvent().withHealthCode(null).build();
        assertValidatorMessage(INSTANCE, event, "healthCode", Validate.CANNOT_BE_BLANK);
    }

}
