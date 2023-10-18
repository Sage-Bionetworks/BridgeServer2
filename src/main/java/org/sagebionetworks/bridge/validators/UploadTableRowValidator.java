package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.upload.UploadTableRow;

/** Validator for Upload table rows. */
public class UploadTableRowValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final UploadTableRowValidator INSTANCE = new UploadTableRowValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return UploadTableRow.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        //noinspection ConstantValue
        if (obj == null) {
            errors.rejectValue("uploadTableRow", "cannot be null");
        } else if (!(obj instanceof UploadTableRow)) {
            errors.rejectValue("uploadTableRow", "is the wrong type");
        } else {
            UploadTableRow row = (UploadTableRow) obj;

            // appId and studyId are required and come from the URL path.
            if (StringUtils.isBlank(row.getAppId())) {
                errors.rejectValue("appId", "is required");
            }
            if (StringUtils.isBlank(row.getStudyId())) {
                errors.rejectValue("studyId", "is required");
            }

            // recordId is required.
            if (StringUtils.isBlank(row.getRecordId())) {
                errors.rejectValue("recordId", "is required");
            }

            // assessmentGuid is required.
            if (StringUtils.isBlank(row.getAssessmentGuid())) {
                errors.rejectValue("assessmentGuid", "is required");
            }

            // createdOn is required.
            if (row.getCreatedOn() == null) {
                errors.rejectValue("createdOn", "is required");
            }

            // healthCode is required.
            if (StringUtils.isBlank(row.getHealthCode())) {
                errors.rejectValue("healthCode", "is required");
            }
        }
    }
}
