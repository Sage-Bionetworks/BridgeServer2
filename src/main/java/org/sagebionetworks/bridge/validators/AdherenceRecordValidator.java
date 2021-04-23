package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;

public class AdherenceRecordValidator extends AbstractValidator {
    
    public static final AdherenceRecordValidator INSTANCE = new AdherenceRecordValidator();
    
    private AdherenceRecordValidator() {}

    @Override
    public void validate(Object obj, Errors errors) {
        AdherenceRecord record = (AdherenceRecord)obj;
        
        if (isBlank(record.getUserId())) {
            errors.rejectValue("userId", CANNOT_BE_BLANK);
        }
        if (isBlank(record.getStudyId())) {
            errors.rejectValue("studyId", CANNOT_BE_BLANK);
        }
        if (isBlank(record.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        if (record.getStartedOn() == null) {
            errors.rejectValue("startedOn", CANNOT_BE_NULL);
        }
        if (record.getEventTimestamp() == null) {
            errors.rejectValue("eventTimestamp", CANNOT_BE_NULL);
        }
    }

}
