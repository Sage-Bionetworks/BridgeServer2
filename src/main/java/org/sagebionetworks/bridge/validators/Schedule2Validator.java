package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.periodInDays;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthLongPeriod;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;

public class Schedule2Validator implements Validator {
    
	public static final Schedule2Validator INSTANCE = new Schedule2Validator();
    
    public static final long FIVE_YEARS_IN_DAYS = 5 * 52 * 7;
    static final String CANNOT_BE_LONGER_THAN_FIVE_YEARS = "cannot be longer than five years";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule2.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Schedule2 schedule = (Schedule2)object;
        
        if (isBlank(schedule.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getOwnerId())) {
            errors.rejectValue("ownerId", CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getAppId())) {
            errors.rejectValue("appId", CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        validateFixedLengthLongPeriod(errors, schedule.getDuration(), "duration", true);
        if (periodInDays(schedule.getDuration()) > FIVE_YEARS_IN_DAYS) {
        	errors.rejectValue("duration", CANNOT_BE_LONGER_THAN_FIVE_YEARS);
        }
        if (schedule.getCreatedOn() == null) {
            errors.rejectValue("createdOn", CANNOT_BE_NULL);
        }
        if (schedule.getModifiedOn() == null) {
            errors.rejectValue("modifiedOn", CANNOT_BE_NULL);
        }
        for (int i=0; i < schedule.getSessions().size(); i++) {
            errors.pushNestedPath("sessions[" + i + "]");
            Session session = schedule.getSessions().get(i);
            
            if (schedule.getDuration() != null) {
                long durationMin = periodInMinutes(schedule.getDuration());
                if (session.getDelay() != null) {
                    long delayMin = periodInMinutes(session.getDelay());
                    if (delayMin >= durationMin) {
                        errors.rejectValue("delay", "cannot be longer than the schedule’s duration");
                    }
                }
                if (session.getInterval() != null) {
                    long intervalMin = periodInMinutes(session.getInterval());
                    if (intervalMin >= durationMin) {
                        errors.rejectValue("interval", "cannot be longer than the schedule’s duration");
                    }
                }
            }
            SessionValidator.INSTANCE.validate(session, errors);
            errors.popNestedPath();
        }
    }
}
