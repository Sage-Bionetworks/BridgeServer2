package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Message;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

// TODO: Period validation in all parts of a schedule, a period can be expressed 
// in minutes, hours, days, or weeks, nothing else.
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
            errors.rejectValue("guid", Validate.CANNOT_BE_BLANK);
        }
        if (isBlank(session.getName())) {
            errors.rejectValue("name", Validate.CANNOT_BE_BLANK);
        }
        if (isBlank(session.getStartEventId())) {
            errors.rejectValue("startEventId", Validate.CANNOT_BE_BLANK);
        }
        if (session.getTimeWindows().isEmpty()) {
            errors.rejectValue("timeWindows", Validate.CANNOT_BE_NULL_OR_EMPTY);
        } else {
            for (int i=0; i < session.getTimeWindows().size(); i++) {
                TimeWindow window = session.getTimeWindows().get(i);
                errors.pushNestedPath("timeWindows["+i+"]");
                
                if (isBlank(window.getGuid())) {
                    errors.rejectValue("guid", Validate.CANNOT_BE_BLANK);
                }
                if (window.getStartTime() == null) {
                    errors.rejectValue("startTime", Validate.CANNOT_BE_NULL);
                }
                errors.popNestedPath();
            }
        }
        if (session.getAssessments().isEmpty()) {
            errors.rejectValue("assessments", Validate.CANNOT_BE_NULL_OR_EMPTY);
        } else {
            Set<String> includedAssessments = new HashSet<>();
            for (int i=0; i < session.getAssessments().size(); i++) {
                AssessmentReference ref = session.getAssessments().get(i);
                
                errors.pushNestedPath("assessments["+i+"]");
                
                if (isBlank(ref.getGuid())) {
                    errors.rejectValue("guid", Validate.CANNOT_BE_BLANK);
                }
                errors.pushNestedPath("assessment");
                
                if (includedAssessments.contains(ref.getAssessmentGuid())) {
                    errors.rejectValue("", "has been included more than once in session");
                }
                includedAssessments.add(ref.getAssessmentGuid());
                
                if (isBlank(ref.getAssessmentGuid())) {
                    errors.rejectValue("guid", Validate.CANNOT_BE_BLANK);
                }
                if (isBlank(ref.getAssessmentAppId())) {
                    errors.rejectValue("appId", Validate.CANNOT_BE_BLANK);
                }
                errors.popNestedPath(); // assessment sub-property
                errors.popNestedPath(); // assessments
            }
        }
        if (session.getNotifyAt() != null) {
            if (session.getRemindAt() != null && session.getRemindMinBefore() == null) {
                errors.rejectValue("remindMinBefore", "must be set if remindAt is set");
            } else if (session.getRemindAt() == null && session.getRemindMinBefore() != null) {
                errors.rejectValue("remindAt", "must be set if remindMinBefore is set");
            }
            if (session.getRemindMinBefore() != null && session.getRemindMinBefore() < 0) {
                errors.rejectValue("remindMinBefore", "cannot be negative");
            }
            Set<String> languageCodes = new HashSet<>();
            if (session.getMessages().isEmpty()) {
                errors.rejectValue("messages", Validate.CANNOT_BE_NULL_OR_EMPTY);
            } else {
                for (int i=0; i < session.getMessages().size(); i++) {
                    Message message = session.getMessages().get(i);
                    errors.pushNestedPath("messages[" + i + "]");
                    
                    if (isBlank(message.getLanguage())) {
                        errors.rejectValue("language", Validate.CANNOT_BE_BLANK);
                    } else {
                        if (languageCodes.contains(message.getLanguage())) {
                            errors.rejectValue("language", "is a duplicate message under the same language code");
                        }
                        languageCodes.add(message.getLanguage());
                        
                        Locale locale = new Locale.Builder().setLanguageTag(message.getLanguage()).build();
                        if (!LocaleUtils.isAvailableLocale(locale)) {
                            errors.rejectValue("language", "is not a valid ISO 639 alpha-2 or alpha-3 language code");
                        }
                    }
                    if (isBlank(message.getSubject())) {
                        errors.rejectValue("subject", Validate.CANNOT_BE_BLANK);
                    } else if (message.getSubject().length() > 40) {
                        errors.rejectValue("subject", "must be 40 characters or less");
                    }
                    if (isBlank(message.getBody())) {
                        errors.rejectValue("body", Validate.CANNOT_BE_BLANK);
                    } else if (message.getBody().length() > 60) {
                        errors.rejectValue("body", "must be 60 characters or less");
                    }
                    errors.popNestedPath();
                }
                if (!languageCodes.contains("en")) {
                    errors.rejectValue("messages", "must contain an English message as a default");
                }
            }
        }
    }
}
