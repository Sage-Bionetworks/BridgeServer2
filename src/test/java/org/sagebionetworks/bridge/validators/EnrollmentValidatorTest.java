package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.EnrollmentValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.Enrollment;

public class EnrollmentValidatorTest {
    
    Enrollment getEnrollment() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setExternalId("anExternalId");
        enrollment.setConsentRequired(true);
        enrollment.setEnrolledOn(CREATED_ON);
        enrollment.setWithdrawnOn(MODIFIED_ON);
        enrollment.setEnrolledBy(TEST_USER_ID);
        enrollment.setWithdrawnBy("withdrawnBy");
        enrollment.setWithdrawalNote("withdrawal note");
        return enrollment;
    }
    
    @Test
    public void validates() {
        Validate.entityThrowingException(INSTANCE, getEnrollment());
    }

    @Test
    public void appIdNull() {
        Enrollment enrollment = getEnrollment();
        enrollment.setAppId(null);
        assertValidatorMessage(INSTANCE, enrollment, "appId", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void appIdBlank() {
        Enrollment enrollment = getEnrollment();
        enrollment.setAppId("");
        assertValidatorMessage(INSTANCE, enrollment, "appId", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void accountIdNull() {
        Enrollment enrollment = getEnrollment();
        enrollment.setAccountId(null);
        assertValidatorMessage(INSTANCE, enrollment, "userId", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void accountIdBlank() {
        Enrollment enrollment = getEnrollment();
        enrollment.setAccountId("  ");
        assertValidatorMessage(INSTANCE, enrollment, "userId", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void studyIdNull() {
        Enrollment enrollment = getEnrollment();
        enrollment.setStudyId(null);
        assertValidatorMessage(INSTANCE, enrollment, "studyId", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void studyIdBlank() {
        Enrollment enrollment = getEnrollment();
        enrollment.setStudyId("");
        assertValidatorMessage(INSTANCE, enrollment, "studyId", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void externalIdBlank() {
        Enrollment enrollment = getEnrollment();
        enrollment.setExternalId("");
        assertValidatorMessage(INSTANCE, enrollment, "externalId", "cannot be blank");
    }
    
    @Test
    public void externalIdNullOK() {
        Enrollment enrollment = getEnrollment();
        enrollment.setExternalId(null);
        Validate.entityThrowingException(INSTANCE, getEnrollment());
    }
}
