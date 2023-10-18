package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

public class UploadTableRowQueryValidatorTest {
    @Test
    public void validQuery() {
        Validate.entityThrowingException(UploadTableRowQueryValidator.INSTANCE, makeValidQuery());
    }

    @Test
    public void withOptionalParams() {
        UploadTableRowQuery query = makeValidQuery();
        query.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        query.setStartTime(TestConstants.TIMESTAMP);
        query.setEndTime(TestConstants.TIMESTAMP.plusHours(1));
        query.setIncludeTestData(true);
        query.setStart(0);
        query.setPageSize(100);
        Validate.entityThrowingException(UploadTableRowQueryValidator.INSTANCE, query);
    }

    @Test
    public void nullAppId() {
        UploadTableRowQuery query = makeValidQuery();
        query.setAppId(null);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "appId", "is required");
    }

    @Test
    public void emptyAppId() {
        UploadTableRowQuery query = makeValidQuery();
        query.setAppId("");
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "appId", "is required");
    }

    @Test
    public void blankAppId() {
        UploadTableRowQuery query = makeValidQuery();
        query.setAppId("   ");
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "appId", "is required");
    }

    @Test
    public void nullStudyId() {
        UploadTableRowQuery query = makeValidQuery();
        query.setStudyId(null);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "studyId", "is required");
    }

    @Test
    public void emptyStudyId() {
        UploadTableRowQuery query = makeValidQuery();
        query.setStudyId("");
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "studyId", "is required");
    }

    @Test
    public void blankStudyId() {
        UploadTableRowQuery query = makeValidQuery();
        query.setStudyId("   ");
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "studyId", "is required");
    }

    @Test
    public void startTimeEqualsEndTime() {
        UploadTableRowQuery query = makeValidQuery();
        query.setStartTime(TestConstants.TIMESTAMP);
        query.setEndTime(TestConstants.TIMESTAMP);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "startTime",
                "must be before endTime");
    }

    @Test
    public void startTimeAfterEndTime() {
        UploadTableRowQuery query = makeValidQuery();
        query.setStartTime(TestConstants.TIMESTAMP.plusHours(1));
        query.setEndTime(TestConstants.TIMESTAMP);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "startTime",
                "must be before endTime");
    }

    @Test
    public void negativeStart() {
        UploadTableRowQuery query = makeValidQuery();
        query.setStart(-1);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "start",
                "must be non-negative");
    }

    @Test
    public void pageSizeTooSmall() {
        UploadTableRowQuery query = makeValidQuery();
        query.setPageSize(4);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "pageSize",
                "must at least 5");
    }

    @Test
    public void pageSizeTooLarge() {
        UploadTableRowQuery query = makeValidQuery();
        query.setPageSize(101);
        assertValidatorMessage(UploadTableRowQueryValidator.INSTANCE, query, "pageSize",
                "must be at most 100");
    }

    private static UploadTableRowQuery makeValidQuery() {
        UploadTableRowQuery query = new UploadTableRowQuery();
        query.setAppId(TestConstants.TEST_APP_ID);
        query.setStudyId(TestConstants.TEST_STUDY_ID);
        return query;
    }
}
