package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.periodInDays;
import static org.sagebionetworks.bridge.BridgeUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_RELAXED_ID_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_RELAXED_ID_PATTERN;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_DUPLICATE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateJsonLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.HashSet;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;

public class Schedule2Validator implements Validator {

    public static final Schedule2Validator INSTANCE = new Schedule2Validator();

    static final String APP_ID_FIELD = "appId";
    static final String CREATED_ON_FIELD = "createdOn";
    static final String DELAY_FIELD = "delay";
    static final String DURATION_FIELD = "duration";
    static final String GUID_FIELD = "guid";
    static final String IDENTIFIER_FIELD = "identifier";
    static final String INTERVAL_FIELD = "interval";
    static final String MODIFIED_ON_FIELD = "modifiedOn";
    static final String NAME_FIELD = "name";
    static final String OCCURRENCES_FIELD = "occurrences";
    static final String ORIGIN_EVENT_ID_FIELD = "originEventId";
    static final String OWNER_ID_FIELD = "ownerId";
    static final String SESSIONS_FIELD = "sessions";
    static final String STUDY_BURSTS_FIELD = "studyBursts";
    static final String UPDATE_TYPE_FIELD = "updateType";
    static final String CLIENT_DATA_FIELD = "clientData";

    public static final long FIVE_YEARS_IN_DAYS = 5 * 365;
    public static final int OCCURRENCE_LIMIT = 12;
    
    static final String CANNOT_BE_LONGER_THAN_FIVE_YEARS = "cannot be longer than five years";
    static final String CANNOT_BE_LONGER_THAN_SCHEDULE = "cannot be longer than the schedule’s duration";
    
    static final String OUT_OF_RANGE_ERROR = "must be from 1-" + OCCURRENCE_LIMIT;

    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule2.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Schedule2 schedule = (Schedule2) object;

        if (isBlank(schedule.getName())) {
            errors.rejectValue(NAME_FIELD, CANNOT_BE_BLANK);
        }
        validateStringLength(errors, 255, schedule.getName(), NAME_FIELD);
        if (isBlank(schedule.getOwnerId())) {
            errors.rejectValue(OWNER_ID_FIELD, CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getAppId())) {
            errors.rejectValue(APP_ID_FIELD, CANNOT_BE_BLANK);
        }
        if (isBlank(schedule.getGuid())) {
            errors.rejectValue(GUID_FIELD, CANNOT_BE_BLANK);
        }
        validateFixedLengthLongPeriod(errors, schedule.getDuration(), DURATION_FIELD, true);
        if (periodInDays(schedule.getDuration()) > FIVE_YEARS_IN_DAYS) {
            errors.rejectValue(DURATION_FIELD, CANNOT_BE_LONGER_THAN_FIVE_YEARS);
        }
        if (schedule.getCreatedOn() == null) {
            errors.rejectValue(CREATED_ON_FIELD, CANNOT_BE_NULL);
        }
        if (schedule.getModifiedOn() == null) {
            errors.rejectValue(MODIFIED_ON_FIELD, CANNOT_BE_NULL);
        }
        validateJsonLength(errors, TEXT_SIZE, schedule.getClientData(), CLIENT_DATA_FIELD);
        Set<String> studyBurstIds = new HashSet<>();

        for (int i = 0; i < schedule.getStudyBursts().size(); i++) {
            errors.pushNestedPath(STUDY_BURSTS_FIELD+"[" + i + "]");
            
            StudyBurst burst = schedule.getStudyBursts().get(i);
            if (isBlank(burst.getIdentifier())) {
                errors.rejectValue(IDENTIFIER_FIELD, CANNOT_BE_BLANK);
            } else if (studyBurstIds.contains(burst.getIdentifier())) {
                errors.rejectValue(IDENTIFIER_FIELD, CANNOT_BE_DUPLICATE);
            } else if (!burst.getIdentifier().matches(BRIDGE_RELAXED_ID_PATTERN)) {
                errors.rejectValue(IDENTIFIER_FIELD, BRIDGE_RELAXED_ID_ERROR);
            }
            validateStringLength(errors, 255, burst.getIdentifier(), IDENTIFIER_FIELD);
            studyBurstIds.add(burst.getIdentifier());
            
            if (isBlank(burst.getOriginEventId())) {
                errors.rejectValue(ORIGIN_EVENT_ID_FIELD, CANNOT_BE_BLANK);
            }
            if (burst.getDelay() != null) {
                validateFixedLengthLongPeriod(errors, burst.getDelay(), DELAY_FIELD, false);
            }
            if (burst.getInterval() == null) {
                errors.rejectValue(INTERVAL_FIELD, CANNOT_BE_NULL);
            } else {
                validateFixedLengthLongPeriod(errors, burst.getInterval(), INTERVAL_FIELD, true);
            }
            if (burst.getOccurrences() == null) {
                errors.rejectValue(OCCURRENCES_FIELD, CANNOT_BE_NULL);
            } else {
                int occurrences = burst.getOccurrences();
                if (occurrences < 1 || occurrences > OCCURRENCE_LIMIT) {
                    errors.rejectValue(OCCURRENCES_FIELD, OUT_OF_RANGE_ERROR);
                }
            }
            if (burst.getUpdateType() == null) {
                errors.rejectValue(UPDATE_TYPE_FIELD, CANNOT_BE_NULL);
            }
            errors.popNestedPath();
        }
        
        SessionValidator sessionValidator = new SessionValidator(schedule.getDuration(), studyBurstIds);
        
        for (int i = 0; i < schedule.getSessions().size(); i++) {
            errors.pushNestedPath(SESSIONS_FIELD+"[" + i + "]");
            Session session = schedule.getSessions().get(i);

            if (schedule.getDuration() != null) {
                long durationMin = periodInMinutes(schedule.getDuration());
                if (session.getDelay() != null) {
                    long delayMin = periodInMinutes(session.getDelay());
                    if (delayMin >= durationMin) {
                        errors.rejectValue(DELAY_FIELD, CANNOT_BE_LONGER_THAN_SCHEDULE);
                    }
                }
                if (session.getInterval() != null) {
                    long intervalMin = periodInMinutes(session.getInterval());
                    if (intervalMin >= durationMin) {
                        errors.rejectValue(INTERVAL_FIELD, CANNOT_BE_LONGER_THAN_SCHEDULE);
                    }
                }
            }
            sessionValidator.validate(session, errors);
            errors.popNestedPath();
        }
    }
}
