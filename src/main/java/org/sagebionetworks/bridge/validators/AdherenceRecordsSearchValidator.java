package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import org.joda.time.DateTime;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public class AdherenceRecordsSearchValidator extends AbstractValidator {
    
    public final static AdherenceRecordsSearchValidator INSTANCE = new AdherenceRecordsSearchValidator();
    
    static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_SET_SIZE = 500;
    private static final int MAX_MAP_SIZE = 50;
    private static final DateTime EARLIEST_DATE = DateTime.parse("2020-01-01T00:00:00.000Z");
    private static final DateTime LATEST_DATE = DateTime.parse("2120-01-01T00:00:00.000Z");
    
    private AdherenceRecordsSearchValidator() {}

    @Override
    public void validate(Object obj, Errors errors) {
        AdherenceRecordsSearch search = (AdherenceRecordsSearch)obj;
        
        if (search.getAssessmentIds().size() > MAX_SET_SIZE) {
            errors.rejectValue("assessmentIds", "exceeds maximum size of " + MAX_SET_SIZE + " entries");
        }
        if (search.getSessionGuids().size() > MAX_SET_SIZE) {
            errors.rejectValue("sessionGuids", "exceeds maximum size of " + MAX_SET_SIZE + " entries");
        }
        if (search.getInstanceGuids().size() > MAX_SET_SIZE) {
            errors.rejectValue("instanceGuids", "exceeds maximum size of " + MAX_SET_SIZE + " entries");
        }
        if (search.getTimeWindowGuids().size() > MAX_SET_SIZE) {
            errors.rejectValue("timeWindowGuids", "exceeds maximum size of " + MAX_SET_SIZE + " entries");
        }
        if (search.getEventTimestamps().size() > MAX_MAP_SIZE) {
            errors.rejectValue("eventTimestamps", "exceeds maximum size of " + MAX_MAP_SIZE + " entries");
        }
        if (search.getStartTime() != null || search.getEndTime() != null) {
            if (search.getStartTime() == null) {
                errors.rejectValue("startTime", "must be provided if endTime is provided");
            } else if (search.getEndTime() == null) {
                errors.rejectValue("endTime", "must be provided if startTime is provided");
            } else {
                if (search.getStartTime().isBefore(EARLIEST_DATE)) {
                    errors.rejectValue("startTime", "is before the earliest allowed time of " 
                            + EARLIEST_DATE.toString());
                }
                if (search.getEndTime().isAfter(LATEST_DATE)) {
                    errors.rejectValue("endTime", "is after the lastest allowed time of " 
                            + LATEST_DATE.toString());
                }
                if (search.getEndTime().isBefore(search.getStartTime())) {
                    errors.rejectValue("endTime", "is before the startTime");
                }
            }
        }
        if (isBlank(search.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_BLANK);
        }
        if (isBlank(search.getStudyId())) {
            errors.rejectValue("studyId", CANNOT_BE_BLANK);
        }
        if (search.getOffsetBy() < 0) {
            errors.rejectValue("offsetBy", "cannot be negative");
        }
        if (search.getPageSize() < 1 || search.getPageSize() > MAX_PAGE_SIZE) {
            errors.rejectValue("pageSize", "must be 1-"+MAX_PAGE_SIZE+" records");
        }
    }
}
