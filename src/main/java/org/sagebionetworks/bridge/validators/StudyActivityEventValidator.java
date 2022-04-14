package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeUtils.isValidTimeZoneID;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_TIME_ZONE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

public class StudyActivityEventValidator extends AbstractValidator implements Validator {

    public static final StudyActivityEventValidator CREATE_INSTANCE = new StudyActivityEventValidator(true);
    public static final StudyActivityEventValidator DELETE_INSTANCE = new StudyActivityEventValidator(false);
    
    static final String CLIENT_TIME_ZONE_FIELD = "clientTimeZone";
    
    private final boolean createOnly;
    
    private StudyActivityEventValidator(boolean createOnly) {
        this.createOnly = createOnly;
    }
    
    @Override
    public void validate(Object object, Errors errors) {
        StudyActivityEvent event = (StudyActivityEvent)object;
        
        if (StringUtils.isBlank(event.getAppId())) {
            errors.rejectValue("appId", CANNOT_BE_BLANK);
        }
        if (StringUtils.isBlank(event.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_BLANK);
        }
        if (StringUtils.isBlank(event.getStudyId())) {
            errors.rejectValue("studyId", CANNOT_BE_BLANK);
        }
        if (StringUtils.isBlank(event.getEventId())) {
            errors.rejectValue("eventId", INVALID_EVENT_ID);
        }
        validateStringLength(errors, 255, event.getAnswerValue(), "answerValue");
        if (createOnly) {
            if (event.getTimestamp() == null) {
                errors.rejectValue("timestamp", CANNOT_BE_NULL);
            }
            if (event.getCreatedOn() == null) {
                errors.rejectValue("createdOn", CANNOT_BE_NULL);
            }
            if (!isValidTimeZoneID(event.getClientTimeZone(), false)) {
                errors.rejectValue(StudyActivityEventValidator.CLIENT_TIME_ZONE_FIELD, INVALID_TIME_ZONE);
            }
        }
    }

}
