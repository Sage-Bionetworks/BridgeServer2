package org.sagebionetworks.bridge.validators;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.exporter.ExportToAppNotification;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;

/** Validator for notifications for exporting a health data record to Synapse for Exporter 3.0. */
public class ExportToAppNotificationValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final ExportToAppNotificationValidator INSTANCE = new ExportToAppNotificationValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return ExportToAppNotification.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        //noinspection ConstantConditions
        if (object == null) {
            errors.rejectValue("ExportToAppNotification", Validate.CANNOT_BE_NULL);
        } else if (!(object instanceof ExportToAppNotification)) {
            errors.rejectValue("ExportToAppNotification", Validate.INVALID_TYPE);
        } else {
            ExportToAppNotification notification = (ExportToAppNotification) object;

            if (StringUtils.isBlank(notification.getAppId())) {
                errors.rejectValue("appId", "is required");
            }

            if (StringUtils.isBlank(notification.getRecordId())) {
                errors.rejectValue("recordId", "is required");
            }

            if (notification.getRecord() != null) {
                errors.pushNestedPath("record");
                validateRecordInfo(notification.getRecord(), errors);
                errors.popNestedPath();
            }

            // Note that getStudyRecords() is never null.
            for (Map.Entry<String, ExportedRecordInfo> studyRecordEntry :
                    notification.getStudyRecords().entrySet()) {
                errors.pushNestedPath("studyRecords{" + studyRecordEntry.getKey() + "}");
                validateRecordInfo(studyRecordEntry.getValue(), errors);
                errors.popNestedPath();
            }
        }
    }

    private static void validateRecordInfo(ExportedRecordInfo recordInfo, Errors errors) {
        if (StringUtils.isBlank(recordInfo.getParentProjectId())) {
            errors.rejectValue("parentProjectId", "is required");
        }

        if (StringUtils.isBlank(recordInfo.getRawFolderId())) {
            errors.rejectValue("rawFolderId", "is required");
        }

        if (StringUtils.isBlank(recordInfo.getFileEntityId())) {
            errors.rejectValue("fileEntityId", "is required");
        }

        if (StringUtils.isBlank(recordInfo.getS3Bucket())) {
            errors.rejectValue("s3Bucket", "is required");
        }

        if (StringUtils.isBlank(recordInfo.getS3Key())) {
            errors.rejectValue("s3Key", "is required");
        }
    }
}
