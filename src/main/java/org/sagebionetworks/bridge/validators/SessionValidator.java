package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateColorScheme;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;

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
            errors.rejectValue("startEventId", INVALID_EVENT_ID);
        }
        validateFixedLengthPeriod(errors, session.getDelay(), "delay", false);
        validateFixedLengthLongPeriod(errors, session.getInterval(), "interval", false);
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
                validateFixedLengthPeriod(errors, window.getExpiration(), "expiration", false);
                if (session.getInterval() != null) {
                    if (window.getExpiration() == null) {
                        errors.rejectValue("expiration", "is required when a session has an interval");
                    } else {
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
                if (isBlank(asmt.getIdentifier())) {
                    errors.rejectValue("identifier", CANNOT_BE_BLANK);
                }
                if (isBlank(asmt.getAppId())) {
                    errors.rejectValue("appId", CANNOT_BE_BLANK);
                }
                validateLabels(errors, asmt.getLabels());
                validateColorScheme(errors, asmt.getColorScheme(), "colorScheme");
                errors.popNestedPath();
            }
        }
    }
}
