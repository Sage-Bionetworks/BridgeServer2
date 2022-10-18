package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class DemographicValuesValidationConfigurationValidatorTest {
    private DemographicValuesValidationConfiguration config;

    @BeforeMethod
    public void beforeMethod() {
        config = new DemographicValuesValidationConfiguration();
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        JsonNode rules = BridgeObjectMapper.get().createObjectNode().set("en",
                BridgeObjectMapper.get().createArrayNode().add("foo").add("bar"));
        config.setValidationRules(rules);
    }

    @Test
    public void supports() {
        assertTrue(new DemographicValuesValidationConfigurationValidator()
                .supports(DemographicValuesValidationConfiguration.class));
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(new DemographicValuesValidationConfigurationValidator(), config);
    }

    @Test
    public void nullType() {
        config.setValidationType(null);
        assertValidatorMessage(new DemographicValuesValidationConfigurationValidator(), config, "validationType",
                CANNOT_BE_NULL);
    }

    @Test
    public void nullRules() {
        config.setValidationRules(null);
        assertValidatorMessage(new DemographicValuesValidationConfigurationValidator(), config, "validationRules",
                CANNOT_BE_NULL);
    }

    @Test
    public void bothNull() {
        config.setValidationType(null);
        config.setValidationRules(null);
        assertValidatorMessage(new DemographicValuesValidationConfigurationValidator(), config, "validationType",
                CANNOT_BE_NULL);
        assertValidatorMessage(new DemographicValuesValidationConfigurationValidator(), config, "validationRules",
                CANNOT_BE_NULL);
    }
}
