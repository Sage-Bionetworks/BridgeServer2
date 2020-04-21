package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.substudies.Substudy;

public class SubstudyValidatorTest {
    private static final SubstudyValidator VALIDATOR = SubstudyValidator.INSTANCE;
    
    private Substudy substudy;
    
    @Test
    public void valid() {
        substudy = Substudy.create();
        substudy.setId("id");
        substudy.setAppId(TEST_APP_ID);
        substudy.setName("name");
        
        Validate.entityThrowingException(VALIDATOR, substudy);
    }
    
    @Test
    public void idIsRequired() {
        substudy = Substudy.create();
        TestUtils.assertValidatorMessage(VALIDATOR, substudy, "id", "is required");
    }
    
    @Test
    public void invalidIdentifier() {
        substudy = Substudy.create();
        substudy.setId("id not valid");
        
        TestUtils.assertValidatorMessage(VALIDATOR, substudy, "id", "must contain only lower- or upper-case letters, numbers, dashes, and/or underscores");
    }

    @Test
    public void nameIsRequired() {
        substudy = Substudy.create();
        TestUtils.assertValidatorMessage(VALIDATOR, substudy, "name", "is required");
    }

    @Test
    public void appIdIsRequired() {
        substudy = Substudy.create();
        TestUtils.assertValidatorMessage(VALIDATOR, substudy, "appId", "is required");
    }
}
