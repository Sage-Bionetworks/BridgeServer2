package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_TIME_ZONE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateJsonLength;

import java.time.ZoneId;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;

public class AdherenceRecordListValidator extends AbstractValidator {
    
    public static final AdherenceRecordListValidator INSTANCE = new AdherenceRecordListValidator();
    
    static final String CLIENT_TIME_ZONE_FIELD = "clientTimeZone";
    static final String EVENT_TIMESTAMP_FIELD = "eventTimestamp";
    static final String INSTANCE_GUID_FIELD = "instanceGuid";
    static final String STARTED_ON_FIELD = "startedOn";
    static final String STUDY_ID_FIELD = "studyId";
    static final String USER_ID_FIELD = "userId";

    private AdherenceRecordListValidator() {}

    @Override
    public void validate(Object obj, Errors errors) {
        AdherenceRecordList list = (AdherenceRecordList)obj;
        
        for (int i=0; i < list.getRecords().size(); i++) {
            AdherenceRecord record = list.getRecords().get(i);
            
            errors.pushNestedPath("records[" + i + "]");
            if (isBlank(record.getUserId())) {
                errors.rejectValue(USER_ID_FIELD, CANNOT_BE_BLANK);
            }
            if (isBlank(record.getStudyId())) {
                errors.rejectValue(STUDY_ID_FIELD, CANNOT_BE_BLANK);
            }
            if (isBlank(record.getInstanceGuid())) {
                errors.rejectValue(INSTANCE_GUID_FIELD, CANNOT_BE_BLANK);
            }
            if (record.getStartedOn() != null && record.getFinishedOn()  != null && 
                    record.getStartedOn().isAfter(record.getFinishedOn())) {
                errors.rejectValue(STARTED_ON_FIELD, "cannot be later than finishedOn");
            }
            if (record.getEventTimestamp() == null) {
                errors.rejectValue(EVENT_TIMESTAMP_FIELD, CANNOT_BE_NULL);
            }
            if (record.getClientTimeZone() != null) {
                try {
                    ZoneId.of(record.getClientTimeZone());
                } catch (Exception e) {
                    errors.rejectValue(CLIENT_TIME_ZONE_FIELD, INVALID_TIME_ZONE);
                }
            }
            validateJsonLength(errors, TEXT_SIZE,record.getClientData(), "clientData");
            errors.popNestedPath();
        }
    }
}
