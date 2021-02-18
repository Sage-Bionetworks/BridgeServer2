package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ActivityEventValidator implements Validator {

    public static final ActivityEventValidator INSTANCE = new ActivityEventValidator();
    
    static final String EVENT_ID_ERROR = "cannot be null (may be missing object or event type)";
    static final String EVENT_ID_IMMUTABLE_ERROR = "is immutable and cannot be changed or deleted";
    static final String ANSWER_VALUE_ERROR = "cannot be null or blank if the event indicates the answer to a survey";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return DynamoActivityEvent.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        DynamoActivityEvent event = (DynamoActivityEvent)object;
        
        String eventId = event.getEventId();
        
        if (eventId == null) {
            errors.rejectValue("eventId", EVENT_ID_ERROR);
        } else if (eventId.endsWith(":answered") && isBlank(event.getAnswerValue())) {
            errors.rejectValue("answerValue", ANSWER_VALUE_ERROR);
        }
        if (event.getTimestamp() == null) {
            errors.rejectValue("timestamp", Validate.CANNOT_BE_NULL);
        }
        if (isBlank(event.getHealthCode())) {
            errors.rejectValue("healthCode", Validate.CANNOT_BE_BLANK);
        }
    }
}
