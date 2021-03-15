package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLanguageSet;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedPeriod;

import java.util.List;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Label;
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
            validateLabels(session.getLabels(), errors);
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
                if (session.getInterval() != null && window.getExpiration() != null) {
                    int intervalMin = periodInMinutes(session.getInterval());
                    int expMin = periodInMinutes(window.getExpiration());
                    if (expMin > intervalMin) {
                        errors.rejectValue("expiration", "cannot be longer in duration than the session’s interval");
                    }
                }
                errors.popNestedPath();
            }
        }
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
                validateLabels(asmt.getLabels(), errors);
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
            validateLanguageSet(errors, session.getMessages(), "messages");
            validateMessageContents(session, errors);
            validateFixedPeriod(errors, session.getReminderPeriod(), "reminderPeriod", false);
        }
    }
    
    public void validateMessageContents(Session session, Errors errors) {
        if (session.getMessages().isEmpty()) {
            return;
        }
        boolean englishDefault = false;
        for (int j=0; j < session.getMessages().size(); j++) {
            NotificationMessage message = session.getMessages().get(j);
            
            if ("en".equalsIgnoreCase(message.getLang())) {
                englishDefault = true;
            }
            errors.pushNestedPath("messages[" + j + "]");
            if (isBlank(message.getSubject())) {
                errors.rejectValue("subject", CANNOT_BE_BLANK);
            } else if (message.getSubject().length() > 40) {
                errors.rejectValue("subject", "must be 40 characters or less");
            }
            if (isBlank(message.getMessage())) {
                errors.rejectValue("message", CANNOT_BE_BLANK);
            } else if (message.getMessage().length() > 60) {
                errors.rejectValue("message", "must be 60 characters or less");
            }
            errors.popNestedPath();
        }
        if (!englishDefault) {
            errors.rejectValue("messages", "must include an English-language message as a default");
        }
    }
    
    private void validateLabels(List<Label> labels, Errors errors) {
        validateLanguageSet(errors, labels, "labels");    
        for (int j=0; j < labels.size(); j++) {
            Label label = labels.get(j);
            
            if (isBlank(label.getValue())) {
                errors.pushNestedPath("labels[" + j + "]");
                errors.rejectValue("value", CANNOT_BE_BLANK);
                errors.popNestedPath();
            }
        }
    }
}
