package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public class AdherenceRecordsSearchValidator extends AbstractValidator {
    
    public final static AdherenceRecordsSearchValidator INSTANCE = new AdherenceRecordsSearchValidator();
    
    private AdherenceRecordsSearchValidator() {}

    @Override
    public void validate(Object obj, Errors errors) {
        AdherenceRecordsSearch search = (AdherenceRecordsSearch)obj;

        if (isBlank(search.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_BLANK);
        }
        if (isBlank(search.getStudyId())) {
            errors.rejectValue("studyId", CANNOT_BE_BLANK);
        }
        if (isNotBlank(search.getStartEventId()) || search.getStartDay() != null || search.getEndDay() != null) {
            if (isBlank(search.getStartEventId())) {
                errors.rejectValue("startEventId", "cannot be null or blank when startDay or endDay is supplied");
            }
            if (search.getStartDay() == null) {
                errors.rejectValue("startDay", "cannot be null when startEventId or endDay is supplied");
            }
            if (search.getEndDay() == null) {
                errors.rejectValue("endDay", "cannot be null when startEventId or startDay is supplied");
            }
        }
        if (search.getOffsetBy() < 0) {
            errors.rejectValue("offsetBy", "cannot be negative");
        }
        if (search.getPageSize() < 5 || search.getPageSize() > 1000) {
            errors.rejectValue("pageSize", "must be 5-1000 records");
        }
    }
}
