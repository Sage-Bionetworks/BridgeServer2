package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public class AdherenceRecordsSearchValidator extends AbstractValidator {
    
    public static final int DEFAULT_PAGE_SIZE = 250;
    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_SET_SIZE = 500;
    public static final int MAX_MAP_SIZE = 50;
    public static final String MAX_SET_SIZE_ERROR = "exceeds maximum size of " + MAX_SET_SIZE + " entries";
    public static final String MAX_MAP_SIZE_ERROR = "exceeds maximum size of " + MAX_MAP_SIZE + " entries";
    public static final String PAGE_SIZE_ERROR = "must be 1-"+MAX_PAGE_SIZE+" records";
    public static final String START_TIME_MISSING = "must be provided if endTime is provided";
    public static final String END_TIME_MISSING = "must be provided if startTime is provided";
    public static final String END_TIME_BEFORE_START_TIME = "is before the startTime";
    
    static final String EVENT_TIMESTAMPS_FIELD = "eventTimestamps";
    static final String TIME_WINDOW_GUIDS_FIELD = "timeWindowGuids";
    static final String INSTANCE_GUIDS_FIELD = "instanceGuids";
    static final String SESSION_GUIDS_FIELD = "sessionGuids";
    static final String ASSESSMENT_IDS_FIELD = "assessmentIds";
    static final String PAGE_SIZE_FIELD = "pageSize";
    static final String OFFSET_BY_FIELD = "offsetBy";
    static final String STUDY_ID_FIELD = "studyId";
    static final String USER_ID_FIELD = "userId";
    static final String START_TIME_FIELD = "startTime";
    static final String END_TIME_FIELD = "endTime";
    
    public final static AdherenceRecordsSearchValidator INSTANCE = new AdherenceRecordsSearchValidator();
    
    private AdherenceRecordsSearchValidator() {}

    @Override
    public void validate(Object obj, Errors errors) {
        AdherenceRecordsSearch search = (AdherenceRecordsSearch)obj;
        
        if (search.getAssessmentIds().size() > MAX_SET_SIZE) {
            errors.rejectValue(ASSESSMENT_IDS_FIELD, MAX_SET_SIZE_ERROR);
        }
        if (search.getSessionGuids().size() > MAX_SET_SIZE) {
            errors.rejectValue(SESSION_GUIDS_FIELD, MAX_SET_SIZE_ERROR);
        }
        if (search.getInstanceGuids().size() > MAX_SET_SIZE) {
            errors.rejectValue(INSTANCE_GUIDS_FIELD, MAX_SET_SIZE_ERROR);
        }
        if (search.getTimeWindowGuids().size() > MAX_SET_SIZE) {
            errors.rejectValue(TIME_WINDOW_GUIDS_FIELD, MAX_SET_SIZE_ERROR);
        }
        if (search.getEventTimestamps().size() > MAX_MAP_SIZE) {
            errors.rejectValue(EVENT_TIMESTAMPS_FIELD, MAX_MAP_SIZE_ERROR);
        }
        if (search.getStartTime() != null || search.getEndTime() != null) {
            if (search.getStartTime() == null) {
                errors.rejectValue(START_TIME_FIELD, START_TIME_MISSING);
            } else if (search.getEndTime() == null) {
                errors.rejectValue(END_TIME_FIELD, END_TIME_MISSING);
            } else if (search.getEndTime().isBefore(search.getStartTime())) {
                errors.rejectValue(END_TIME_FIELD, END_TIME_BEFORE_START_TIME);
            }
        }
        if (isBlank(search.getUserId())) {
            errors.rejectValue(USER_ID_FIELD, CANNOT_BE_BLANK);
        }
        if (isBlank(search.getStudyId())) {
            errors.rejectValue(STUDY_ID_FIELD, CANNOT_BE_BLANK);
        }
        if (search.getOffsetBy() < 0) {
            errors.rejectValue(OFFSET_BY_FIELD, CANNOT_BE_NEGATIVE);
        }
        if (search.getPageSize() < 1 || search.getPageSize() > MAX_PAGE_SIZE) {
            errors.rejectValue(PAGE_SIZE_FIELD, PAGE_SIZE_ERROR);
        }
    }
}
