package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.CLIENT_TIME_ZONE_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.TIME_ZONE_ERROR;

import java.time.ZoneId;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

public class StudyActivityEventValidator extends AbstractValidator implements Validator {

    public static final StudyActivityEventValidator INSTANCE = new StudyActivityEventValidator();
    
    private StudyActivityEventValidator() {}
    
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
            errors.rejectValue("eventId", CANNOT_BE_BLANK);
        }
        if (event.getTimestamp() == null) {
            errors.rejectValue("timestamp", CANNOT_BE_NULL);
        }
        if (event.getCreatedOn() == null) {
            errors.rejectValue("createdOn", CANNOT_BE_NULL);
        }
        if (event.getUpdateType() == null) {
            errors.rejectValue("updateType", CANNOT_BE_NULL);
        }
        if (event.getClientTimeZone() != null) {
            try {
                ZoneId.of(event.getClientTimeZone());
            } catch (Exception e) {
                errors.rejectValue(CLIENT_TIME_ZONE_FIELD, TIME_ZONE_ERROR);
            }
        }
    }

}
