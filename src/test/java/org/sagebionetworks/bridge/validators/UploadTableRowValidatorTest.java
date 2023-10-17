package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.upload.UploadTableRow;

public class UploadTableRowValidatorTest {
    private static final int PARTICIPANT_VERSION = 13;
    private static final String RECORD_ID = "test-record";

    private static final String METADATA_KEY = "metadata-key";
    private static final String METADATA_VALUE = "metadata-value";
    private static final Map<String, String> METADATA_MAP = ImmutableMap.of(METADATA_KEY, METADATA_VALUE);

    private static final String DATA_KEY = "data-key";
    private static final String DATA_VALUE = "data-value";
    private static final Map<String, String> DATA_MAP = ImmutableMap.of(DATA_KEY, DATA_VALUE);

    @Test
    public void validRow() {
        Validate.entityThrowingException(UploadTableRowValidator.INSTANCE, makeValidRow());
    }

    @Test
    public void withOptionalParams() {
        UploadTableRow row = makeValidRow();
        row.setTestData(true);
        row.setParticipantVersion(PARTICIPANT_VERSION);
        row.setMetadata(METADATA_MAP);
        row.setData(DATA_MAP);
        Validate.entityThrowingException(UploadTableRowValidator.INSTANCE, row);
    }

    @Test
    public void nullAppId() {
        UploadTableRow row = makeValidRow();
        row.setAppId(null);
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "appId", "is required");
    }

    @Test
    public void emptyAppId() {
        UploadTableRow row = makeValidRow();
        row.setAppId("");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "appId", "is required");
    }

    @Test
    public void blankAppId() {
        UploadTableRow row = makeValidRow();
        row.setAppId("   ");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "appId", "is required");
    }

    @Test
    public void nullStudyId() {
        UploadTableRow row = makeValidRow();
        row.setStudyId(null);
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "studyId", "is required");
    }

    @Test
    public void emptyStudyId() {
        UploadTableRow row = makeValidRow();
        row.setStudyId("");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "studyId", "is required");
    }

    @Test
    public void blankStudyId() {
        UploadTableRow row = makeValidRow();
        row.setStudyId("   ");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "studyId", "is required");
    }

    @Test
    public void nullRecordId() {
        UploadTableRow row = makeValidRow();
        row.setRecordId(null);
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "recordId", "is required");
    }

    @Test
    public void emptyRecordId() {
        UploadTableRow row = makeValidRow();
        row.setRecordId("");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "recordId", "is required");
    }

    @Test
    public void blankRecordId() {
        UploadTableRow row = makeValidRow();
        row.setRecordId("   ");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "recordId", "is required");
    }

    @Test
    public void nullAssessmentGuid() {
        UploadTableRow row = makeValidRow();
        row.setAssessmentGuid(null);
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "assessmentGuid", "is required");
    }

    @Test
    public void emptyAssessmentGuid() {
        UploadTableRow row = makeValidRow();
        row.setAssessmentGuid("");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "assessmentGuid", "is required");
    }

    @Test
    public void blankAssessmentGuid() {
        UploadTableRow row = makeValidRow();
        row.setAssessmentGuid("   ");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "assessmentGuid", "is required");
    }

    @Test
    public void nullCreatedOn() {
        UploadTableRow row = makeValidRow();
        row.setCreatedOn(null);
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "createdOn", "is required");
    }

    @Test
    public void nullHealthCode() {
        UploadTableRow row = makeValidRow();
        row.setHealthCode(null);
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "healthCode", "is required");
    }

    @Test
    public void emptyHealthCode() {
        UploadTableRow row = makeValidRow();
        row.setHealthCode("");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "healthCode", "is required");
    }

    @Test
    public void blankHealthCode() {
        UploadTableRow row = makeValidRow();
        row.setHealthCode("   ");
        assertValidatorMessage(UploadTableRowValidator.INSTANCE, row, "healthCode", "is required");
    }

    private static UploadTableRow makeValidRow() {
        UploadTableRow row = UploadTableRow.create();
        row.setAppId(TestConstants.TEST_APP_ID);
        row.setStudyId(TestConstants.TEST_STUDY_ID);
        row.setRecordId(RECORD_ID);
        row.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        row.setCreatedOn(TestConstants.TIMESTAMP);
        row.setHealthCode(TestConstants.HEALTH_CODE);
        return row;
    }
}
