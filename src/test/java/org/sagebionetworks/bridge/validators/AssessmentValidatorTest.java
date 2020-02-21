package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.AssessmentValidator.CANNOT_BE_BLANK;

import java.util.Optional;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;

public class AssessmentValidatorTest {

    AssessmentValidator validator;
    
    Assessment assessment;

    @BeforeMethod
    public void beforeMethod() {
        assessment = AssessmentTest.createAssessment();
        validator = new AssessmentValidator(Optional.empty());
    }
    
    @Test
    public void validAssessment() {
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void guidNull() {
        assessment.setGuid(null);
        assertValidatorMessage(validator, assessment, "guid", CANNOT_BE_BLANK);
    }
    @Test
    public void guidEmpty() {
        assessment.setGuid("  ");
        assertValidatorMessage(validator, assessment, "guid", CANNOT_BE_BLANK);
    }
    @Test
    public void titleNull() {
        assessment.setTitle(null);
        assertValidatorMessage(validator, assessment, "title", CANNOT_BE_BLANK);
    }
    @Test
    public void titleEmpty() {
        assessment.setTitle("");
        assertValidatorMessage(validator, assessment, "title", CANNOT_BE_BLANK);
    }
    @Test
    public void osNameNull() {
        assessment.setOsName(null);
        assertValidatorMessage(validator, assessment, "osName", CANNOT_BE_BLANK);
    }
    @Test
    public void osNameEmpty() {
        assessment.setOsName("\n");
        assertValidatorMessage(validator, assessment, "osName", CANNOT_BE_BLANK);
    }
    @Test
    public void osNameInvalid() {
        assessment.setOsName("webOS");
        assertValidatorMessage(validator, assessment, "osName", "is not a supported platform");
    }
    @Test
    public void identifierNull() {
        assessment.setIdentifier(null);
        assertValidatorMessage(validator, assessment, "identifier", CANNOT_BE_BLANK);
    }
    @Test
    public void identifierEmpty() {
        assessment.setIdentifier("   ");
        assertValidatorMessage(validator, assessment, "identifier", CANNOT_BE_BLANK);
    }
    @Test
    public void identifierAlreadyUsed() {
        validator = new AssessmentValidator(Optional.of(new Assessment()));
        assertValidatorMessage(validator, assessment, "identifier", "already exists in revision 5");
    }
    @Test
    public void identifierInvalid() {
        assessment.setIdentifier("spaces are not allowed");
        assertValidatorMessage(validator, assessment, "identifier", BRIDGE_EVENT_ID_ERROR);
    }
    @Test
    public void revisionNegative() {
        assessment.setRevision(-3);
        assertValidatorMessage(validator, assessment, "revision", "cannot be negative");
    }
    @Test
    public void ownerIdNull() {
        assessment.setOwnerId(null);
        assertValidatorMessage(validator, assessment, "ownerId", CANNOT_BE_BLANK);
    }
    @Test
    public void ownerIdEmpty() {
        assessment.setOwnerId("\t");
        assertValidatorMessage(validator, assessment, "ownerId", CANNOT_BE_BLANK);
    }
}
