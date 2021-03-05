package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;

public class Schedule2Validator implements Validator {
    
    public static final Schedule2Validator INSTANCE = new Schedule2Validator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule2.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Schedule2 schedule = (Schedule2)object;
        
        if (isBlank(schedule.getName())) {
            errors.rejectValue("name", Validate.CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getOwnerId())) {
            errors.rejectValue("ownerId", "is not a valid organization ID");
        }
        if (isBlank(schedule.getAppId())) {
            errors.rejectValue("appId", Validate.CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getGuid())) {
            errors.rejectValue("guid", Validate.CANNOT_BE_BLANK);
        }
        // Right now this parsing is happening during deserialization, which
        // is odd for validation. We might prefer to validate this as a string
        // but persist it as a Period to provide better error feedback.
        if (schedule.getDuration() == null) {
            errors.rejectValue("duration", Validate.CANNOT_BE_NULL);
        }
        if (isBlank(schedule.getDurationStartEventId())) {
            errors.rejectValue("durationStartEventId", "is not a valid event ID");
        }
        if (schedule.getCreatedOn() == null) {
            errors.rejectValue("createdOn", Validate.CANNOT_BE_NULL);
        }
        if (schedule.getModifiedOn() == null) {
            errors.rejectValue("modifiedOn", Validate.CANNOT_BE_NULL);
        }
        for (int i=0; i < schedule.getSessions().size(); i++) {
            errors.pushNestedPath("sessions[" + i + "]");
            Session session = schedule.getSessions().get(i);
            SessionValidator.INSTANCE.validate(session, errors);
            errors.popNestedPath();
        }
    }
}
