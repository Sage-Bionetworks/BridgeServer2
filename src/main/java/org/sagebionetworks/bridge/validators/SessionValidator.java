package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateMessages;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class SessionValidator implements Validator {

    public static final SessionValidator INSTANCE = new SessionValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Session.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Session session = (Session)obj;
        
        if (isBlank(session.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        if (isBlank(session.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        if (isBlank(session.getStartEventId())) {
            errors.rejectValue("startEventId", CANNOT_BE_BLANK);
        }
        validateFixedPeriod(errors, session.getDelay(), "delay", false);
        validateFixedLongPeriod(errors, session.getInterval(), "interval", false);
        if (session.getPerformanceOrder() == null) {
            errors.rejectValue("performanceOrder", CANNOT_BE_NULL);
        }
        if (!session.getLabels().isEmpty()) {
            validateLabels(errors, session.getLabels());
        }
        if (session.getTimeWindows().isEmpty()) {
            errors.rejectValue("timeWindows", CANNOT_BE_NULL_OR_EMPTY);
        } else {
            for (int i=0; i < session.getTimeWindows().size(); i++) {
                TimeWindow window = session.getTimeWindows().get(i);
                errors.pushNestedPath("timeWindows["+i+"]");
                
                if (isBlank(window.getGuid())) {
                    errors.rejectValue("guid", CANNOT_BE_BLANK);
                }
                if (window.getStartTime() == null) {
                    errors.rejectValue("startTime", CANNOT_BE_NULL);
                }
                validateFixedPeriod(errors, window.getExpiration(), "expiration", false);
                if (session.getInterval() != null) {
                    if (window.getExpiration() == null) {
                        errors.rejectValue("expiration", "is required when a session has an interval");
                    } else if (window.getExpiration() != null) {
                        int intervalMin = periodInMinutes(session.getInterval());
                        int expMin = periodInMinutes(window.getExpiration());
                        if (expMin > intervalMin) {
                            errors.rejectValue("expiration", "cannot be longer in duration than the session’s interval");
                        }
                    }
                }
                errors.popNestedPath();
            }
        }
        // Note however that an easy way to schedule a notification would be to create a session
        // with notification info, but no assessments. So we might allow this at a later time.
        if (session.getAssessments().isEmpty()) {
            errors.rejectValue("assessments", CANNOT_BE_NULL_OR_EMPTY);
        } else {
            for (int i=0; i < session.getAssessments().size(); i++) {
                AssessmentReference asmt = session.getAssessments().get(i);
                
                errors.pushNestedPath("assessments["+i+"]");
                if (isBlank(asmt.getGuid())) {
                    errors.rejectValue("guid", CANNOT_BE_BLANK);
                }
                if (isBlank(asmt.getAppId())) {
                    errors.rejectValue("appId", CANNOT_BE_BLANK);
                }
                ValidatorUtils.validateLabels(errors, asmt.getLabels());
                errors.popNestedPath();
            }
        }
        // Notifications are off. We don't want any of the fields set so the designer isn't
        // confused about what will happen.
        if (session.getNotifyAt() == null) {
            if (session.getRemindAt() != null) {
                errors.rejectValue("remindAt", "cannot be set if notifications are disabled");
            }
            if (session.getReminderPeriod() != null) {
                errors.rejectValue("reminderPeriod", "cannot be set if notifications are disabled");
            }
            if (session.isAllowSnooze()) {
                errors.rejectValue("allowSnooze", "cannot be true if notifications are disabled");
            }
            if (!session.getMessages().isEmpty()) {
                errors.rejectValue("messages", "cannot be set if notifications are disabled");
            }
        } else {
            if (session.getRemindAt() != null && session.getReminderPeriod() == null) {
                errors.rejectValue("reminderPeriod", "must be set if remindAt is set");
            } else if (session.getRemindAt() == null && session.getReminderPeriod() != null) {
                errors.rejectValue("remindAt", "must be set if reminderPeriod is set");
            }
            if (session.getNotifyAt() != null && session.getMessages().isEmpty()) {
                errors.rejectValue("messages", CANNOT_BE_NULL_OR_EMPTY);
            }
            validateMessages(errors, session.getMessages());
            validateFixedPeriod(errors, session.getReminderPeriod(), "reminderPeriod", false);
        }
    }
    
}
