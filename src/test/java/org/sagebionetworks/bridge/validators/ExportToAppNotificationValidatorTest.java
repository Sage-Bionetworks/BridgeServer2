package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.exporter.ExportToAppNotification;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;

public class ExportToAppNotificationValidatorTest {
    private static final String FILE_ENTITY_ID = "syn1111";
    private static final String PARENT_PROJECT_ID = "syn2222";
    private static final String RAW_FOLDER_ID = "syn3333";
    private static final String RECORD_ID = "test-record-id";
    private static final String S3_BUCKET = "test-s3-bucket";
    private static final String S3_KEY = "test-s3-key";

    @Test
    public void validNotificationWithNoRecords() {
        // This should never happen, but treat it as valid to simplify logic and tests.
        Validate.entityThrowingException(ExportToAppNotificationValidator.INSTANCE, makeValidNotification());
    }

    @Test
    public void validNotificationWithAppRecord() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setRecord(makeValidRecordInfo());
        Validate.entityThrowingException(ExportToAppNotificationValidator.INSTANCE, notification);
    }

    @Test
    public void validNotificationWithStudyRecord() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setStudyRecords(ImmutableMap.of(TestConstants.TEST_STUDY_ID, makeValidRecordInfo()));
        Validate.entityThrowingException(ExportToAppNotificationValidator.INSTANCE, notification);
    }

    @Test
    public void nullAppId() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setAppId(null);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification, "appId",
                "is required");
    }

    @Test
    public void emptyAppId() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setAppId("");
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification, "appId",
                "is required");
    }

    @Test
    public void blankAppId() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setAppId("   ");
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification, "appId",
                "is required");
    }

    @Test
    public void nullRecordId() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setRecordId(null);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification, "recordId",
                "is required");
    }

    @Test
    public void emptyRecordId() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setRecordId("");
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification, "recordId",
                "is required");
    }

    @Test
    public void blankRecordId() {
        ExportToAppNotification notification = makeValidNotification();
        notification.setRecordId("   ");
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification, "recordId",
                "is required");
    }

    @Test
    public void nullParentProjectId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setParentProjectId(null);
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.parentProjectId", "is required");
    }

    @Test
    public void emptyParentProjectId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setParentProjectId("");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.parentProjectId", "is required");
    }

    @Test
    public void blankParentProjectId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setParentProjectId("   ");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.parentProjectId", "is required");
    }

    @Test
    public void nullRawFolderId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setRawFolderId(null);
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.rawFolderId", "is required");
    }

    @Test
    public void emptyRawFolderId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setRawFolderId("");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.rawFolderId", "is required");
    }

    @Test
    public void blankRawFolderId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setRawFolderId("   ");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.rawFolderId", "is required");
    }

    @Test
    public void nullFileEntityId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setFileEntityId(null);
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.fileEntityId", "is required");
    }

    @Test
    public void emptyFileEntityId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setFileEntityId("");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.fileEntityId", "is required");
    }

    @Test
    public void blankFileEntityId() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setFileEntityId("   ");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.fileEntityId", "is required");
    }

    @Test
    public void nullS3Bucket() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setS3Bucket(null);
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.s3Bucket", "is required");
    }

    @Test
    public void emptyS3Bucket() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setS3Bucket("");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.s3Bucket", "is required");
    }

    @Test
    public void blankS3Bucket() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setS3Bucket("   ");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.s3Bucket", "is required");
    }

    @Test
    public void nullS3Key() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setS3Key(null);
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.s3Key", "is required");
    }

    @Test
    public void emptyS3Key() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setS3Key("");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.s3Key", "is required");
    }

    @Test
    public void blankS3Key() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setS3Key("   ");
        notification.setRecord(recordInfo);
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "record.s3Key", "is required");
    }

    @Test
    public void invalidStudyRecord() {
        ExportToAppNotification notification = makeValidNotification();
        ExportedRecordInfo recordInfo = makeValidRecordInfo();
        recordInfo.setParentProjectId(null);
        notification.setStudyRecords(ImmutableMap.of(TestConstants.TEST_STUDY_ID, recordInfo));
        assertValidatorMessage(ExportToAppNotificationValidator.INSTANCE, notification,
                "studyRecords{" + TestConstants.TEST_STUDY_ID + "}.parentProjectId", "is required");
    }

    private static ExportToAppNotification makeValidNotification() {
        ExportToAppNotification notification = new ExportToAppNotification();
        notification.setAppId(TestConstants.TEST_APP_ID);
        notification.setRecordId(RECORD_ID);
        return notification;
    }

    private static ExportedRecordInfo makeValidRecordInfo() {
        ExportedRecordInfo recordInfo = new ExportedRecordInfo();
        recordInfo.setParentProjectId(PARENT_PROJECT_ID);
        recordInfo.setRawFolderId(RAW_FOLDER_ID);
        recordInfo.setFileEntityId(FILE_ENTITY_ID);
        recordInfo.setS3Bucket(S3_BUCKET);
        recordInfo.setS3Key(S3_KEY);
        return recordInfo;
    }
}
