package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.appconfig.AppConfigEnumId.STUDY_DISEASES;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

public class AppConfigElementValidatorTest {
    private static final AppConfigElementValidator VALIDATOR = AppConfigElementValidator.INSTANCE;
    
    private AppConfigElement element;
    
    @BeforeMethod
    public void before() {
        element = TestUtils.getAppConfigElement();
    }
    
    @Test
    public void valid() {
        Validate.entityThrowingException(VALIDATOR, element);
    }
    
    @Test
    public void idRequired() {
        element.setId(null);
        assertValidatorMessage(VALIDATOR, element, "id", "is required");
        
        element.setId("");
        assertValidatorMessage(VALIDATOR, element, "id", "is required");
    }
    
    @Test
    public void idInvalid() {
        element.setId("@bad");
        assertValidatorMessage(VALIDATOR, element, "id", BRIDGE_EVENT_ID_ERROR);
    }
    
    @Test
    public void idInvalidSystemId() {
        element.setId("bridge:foo");
        assertValidatorMessage(VALIDATOR, element, "id", "not a valid system configuration key");
    }
    
    @Test
    public void idValidSystemId() {
        element.setId(STUDY_DISEASES.getAppConfigKey());
        Validate.entityThrowingException(VALIDATOR, element);
    }
    
    @Test
    public void revisionRequired() {
        element.setRevision(null);
        assertValidatorMessage(VALIDATOR, element, "revision", "is required");
        
        element.setRevision(-3L);
        assertValidatorMessage(VALIDATOR, element, "revision", "must be positive");
        
        element.setRevision(0L);
        assertValidatorMessage(VALIDATOR, element, "revision", "must be positive");
    }
    
    @Test
    public void dataRequired() {
        element.setData(null);
        assertValidatorMessage(VALIDATOR, element, "data", "is required");
    }
}
