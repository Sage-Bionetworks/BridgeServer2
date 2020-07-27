package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.studies.Study;

public class StudyValidatorTest {
    private static final StudyValidator VALIDATOR = StudyValidator.INSTANCE;
    
    private Study study;
    
    @Test
    public void valid() {
        study = Study.create();
        study.setId("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        
        Validate.entityThrowingException(VALIDATOR, study);
    }
    
    @Test
    public void idIsRequired() {
        study = Study.create();
        TestUtils.assertValidatorMessage(VALIDATOR, study, "id", "is required");
    }
    
    @Test
    public void invalidIdentifier() {
        study = Study.create();
        study.setId("id not valid");
        
        TestUtils.assertValidatorMessage(VALIDATOR, study, "id", "must contain only lower- or upper-case letters, numbers, dashes, and/or underscores");
    }

    @Test
    public void nameIsRequired() {
        study = Study.create();
        TestUtils.assertValidatorMessage(VALIDATOR, study, "name", "is required");
    }

    @Test
    public void appIdIsRequired() {
        study = Study.create();
        TestUtils.assertValidatorMessage(VALIDATOR, study, "appId", "is required");
    }
}
