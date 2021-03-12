package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLanguageSet;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validatePeriod;

import java.util.List;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Label;
import org.sagebionetworks.bridge.models.schedules2.Message;
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
        validatePeriod(errors, session.getDelay(), "delay", false);
        validatePeriod(errors, session.getInterval(), "interval", false);
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
                validatePeriod(errors, window.getExpiration(), "expiration", false);
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
        if (session.getRemindAt() != null && session.getReminderPeriod() == null) {
            errors.rejectValue("reminderPeriod", "must be set if remindAt is set");
        } else if (session.getRemindAt() == null && session.getReminderPeriod() != null) {
            errors.rejectValue("remindAt", "must be set if reminderPeriod is set");
        }
        if (session.getNotifyAt() == null && session.isAllowSnooze()) {
            errors.rejectValue("allowSnooze", "cannot be true if notifications are disabled");
        }
        validatePeriod(errors, session.getReminderPeriod(), "reminderPeriod", false);

        // If notifications are turned off, you do not need to provide messages. If you have 
        // notifications enabled, you must have messages. Either way, message contents and 
        // language constraints must always be correct.
        if (session.getNotifyAt() != null && session.getMessages().isEmpty()) {
            errors.rejectValue("messages", CANNOT_BE_NULL_OR_EMPTY);
        }
        validateLanguageSet(errors, session.getMessages(), "messages");
        validateMessageContents(session, errors);
    }

    public void validateMessageContents(Session session, Errors errors) {
        for (int j=0; j < session.getMessages().size(); j++) {
            Message message = session.getMessages().get(j);
            
            errors.pushNestedPath("messages[" + j + "]");
            if (isBlank(message.getSubject())) {
                errors.rejectValue("subject", CANNOT_BE_BLANK);
            } else if (message.getSubject().length() > 40) {
                errors.rejectValue("subject", "must be 40 characters or less");
            }
            if (isBlank(message.getBody())) {
                errors.rejectValue("body", CANNOT_BE_BLANK);
            } else if (message.getBody().length() > 60) {
                errors.rejectValue("body", "must be 60 characters or less");
            }
            errors.popNestedPath();
        }
    }
    
    private void validateLabels(List<Label> labels, Errors errors) {
        validateLanguageSet(errors, labels, "labels");    
        for (int j=0; j < labels.size(); j++) {
            Label label = labels.get(j);
            
            if (isBlank(label.getLabel())) {
                errors.pushNestedPath("labels[" + j + "]");
                errors.rejectValue("label", CANNOT_BE_BLANK);
                errors.popNestedPath();
            }
        }
    }
}
