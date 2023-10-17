package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

/** Validator for Upload table row queries. */
public class UploadTableRowQueryValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final UploadTableRowQueryValidator INSTANCE = new UploadTableRowQueryValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return UploadTableRowQuery.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        //noinspection ConstantValue
        if (obj == null) {
            errors.rejectValue("uploadTableRowQuery", "cannot be null");
        } else if (!(obj instanceof UploadTableRowQuery)) {
            errors.rejectValue("uploadTableRowQuery", "is the wrong type");
        } else {
            UploadTableRowQuery query = (UploadTableRowQuery) obj;

            // appId and studyId are required and come from the URL path.
            if (StringUtils.isBlank(query.getAppId())) {
                errors.rejectValue("appId", "is required");
            }
            if (StringUtils.isBlank(query.getStudyId())) {
                errors.rejectValue("studyId", "is required");
            }

            // If startTime and endTime are both specified, startTime must be before endTime.
            if (query.getStartTime() != null && query.getEndTime() != null
                    && !query.getStartTime().isBefore(query.getEndTime())) {
                errors.rejectValue("startTime", "must be before endTime");
            }

            // Validate paging parameters.
            if (query.getStart() != null && query.getStart() < 0) {
                errors.rejectValue("start", "must be non-negative");
            }
            if (query.getPageSize() != null) {
                if (query.getPageSize() < BridgeConstants.API_MINIMUM_PAGE_SIZE) {
                    errors.rejectValue("pageSize", "must at least 5");
                } else if (query.getPageSize() > BridgeConstants.API_MAXIMUM_PAGE_SIZE) {
                    errors.rejectValue("pageSize", "must be at most 100");
                }
            }
        }
    }
}
