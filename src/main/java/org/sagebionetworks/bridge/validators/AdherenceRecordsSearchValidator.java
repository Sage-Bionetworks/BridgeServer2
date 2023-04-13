package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;

import org.joda.time.Days;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public class AdherenceRecordsSearchValidator extends AbstractValidator {
    
    public static final int DEFAULT_PAGE_SIZE = 250;
    public static final int MAX_DATE_RANGE_IN_DAYS = 60;
    public static final int MAX_PAGE_SIZE = 500;
    public static final int MAX_SET_SIZE = 500;
    public static final int MAX_MAP_SIZE = 50;
    public static final String APP_OR_USER_REQUIRED_ERROR = "appId or userId is required";
    public static final String APP_AND_USER_CANT_BOTH_BE_SPECIFIED_ERROR = "appId and userId can't both be specified";
    public static final String MAX_SET_SIZE_ERROR = "exceeds maximum size of " + MAX_SET_SIZE + " entries";
    public static final String MAX_MAP_SIZE_ERROR = "exceeds maximum size of " + MAX_MAP_SIZE + " entries";
    public static final String PAGE_SIZE_ERROR = "must be 1-"+MAX_PAGE_SIZE+" records";
    public static final String START_TIME_MISSING = "must be provided if endTime is provided";
    public static final String END_TIME_MISSING = "must be provided if startTime is provided";
    public static final String END_TIME_BEFORE_START_TIME = "is before the startTime";
    public static final String EVENT_TIMESTAMP_START_MISSING =
            "must be provided if eventTimestampEnd, hasMultipleUploadIds, or hasNoUploadIds are provided";
    public static final String EVENT_TIMESTAMP_END_MISSING =
            "must be provided if eventTimestampStart, hasMultipleUploadIds, or hasNoUploadIds is provided";
    public static final String EVENT_TIMESTAMP_END_MUST_BE_AFTER_START =
            "eventTimestampEnd must be after eventTimestampStart";
    public static final String EVENT_TIMESTAMP_END_MUST_BE_WITHIN_RANGE =
            "must be within " + MAX_DATE_RANGE_IN_DAYS + " days of eventTimestampStart";
    public static final String HAS_NO_HAS_MULTIPLE_UPLOAD_IDS_ERROR =
            "cannot specify both hasMultipleUploadIds and hasNoUploadIds";

    static final String EVENT_TIMESTAMPS_FIELD = "eventTimestamps";
    static final String EVENT_TIMESTAMP_START_FIELD = "eventTimestampStart";
    static final String EVENT_TIMESTAMP_END_FIELD = "eventTimestampEnd";
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

        // Validate event timestamp start and end.
        if (search.getEventTimestampStart() != null || search.getEventTimestampEnd() != null) {
            if (search.getEventTimestampStart() == null) {
                errors.rejectValue(EVENT_TIMESTAMP_START_FIELD, EVENT_TIMESTAMP_START_MISSING);
            } else if (search.getEventTimestampEnd() == null) {
                errors.rejectValue(EVENT_TIMESTAMP_END_FIELD, EVENT_TIMESTAMP_END_MISSING);
            } else if (!search.getEventTimestampEnd().isAfter(search.getEventTimestampStart())) {
                errors.rejectValue(EVENT_TIMESTAMP_END_FIELD, EVENT_TIMESTAMP_END_MUST_BE_AFTER_START);
            } else if (Days.daysBetween(search.getEventTimestampStart(), search.getEventTimestampEnd()).getDays()
                    > MAX_DATE_RANGE_IN_DAYS) {
                errors.rejectValue(EVENT_TIMESTAMP_END_FIELD, EVENT_TIMESTAMP_END_MUST_BE_WITHIN_RANGE);
            }
        }

        // User ID can be blank for study-scoped searches of the data
        if (isBlank(search.getStudyId())) {
            errors.rejectValue(STUDY_ID_FIELD, CANNOT_BE_BLANK);
        }

        // Either user ID or app ID are required.
        if (isBlank(search.getAppId()) && isBlank(search.getUserId())) {
            errors.reject(APP_OR_USER_REQUIRED_ERROR);
        } else if (isNotBlank(search.getAppId()) && isNotBlank(search.getUserId())) {
            errors.reject(APP_AND_USER_CANT_BOTH_BE_SPECIFIED_ERROR);
        }

        if (search.getOffsetBy() < 0) {
            errors.rejectValue(OFFSET_BY_FIELD, CANNOT_BE_NEGATIVE);
        }
        if (search.getPageSize() < 1 || search.getPageSize() > MAX_PAGE_SIZE) {
            errors.rejectValue(PAGE_SIZE_FIELD, PAGE_SIZE_ERROR);
        }

        // Validate hasMultiple/hasNoUploadIds.
        if (search.hasMultipleUploadIds() || search.hasNoUploadIds()) {
            if (search.hasMultipleUploadIds() && search.hasNoUploadIds()) {
                errors.reject(HAS_NO_HAS_MULTIPLE_UPLOAD_IDS_ERROR);
            }

            // We require eventTimestampStart and eventTimestampEnd, so that we don't have to do a full table scan.
            if (search.getEventTimestampStart() == null) {
                errors.rejectValue(EVENT_TIMESTAMP_START_FIELD, EVENT_TIMESTAMP_START_MISSING);
            }
            if (search.getEventTimestampEnd() == null) {
                errors.rejectValue(EVENT_TIMESTAMP_END_FIELD, EVENT_TIMESTAMP_END_MISSING);
            }
        }
    }
}
