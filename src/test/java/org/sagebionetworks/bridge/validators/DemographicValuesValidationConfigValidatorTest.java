package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.testng.Assert.assertTrue;

import java.io.IOException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.sagebionetworks.bridge.models.demographics.DemographicValuesValidationConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class DemographicValuesValidationConfigValidatorTest {
    private static final String CATEGORY_NAME = "category1";
    private static final String INVALID_VALIDATION_RULES = "invalid validation rules";
    private static final String INVALID_CONFIGURATION_BAD_LANGUAGE_CODE = "bad language code";
    private static final String INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX = "min cannot be larger than max";

    private Demographic demographic;
    private DemographicValuesValidationConfig config;

    @BeforeMethod
    public void beforeMethod() {
        demographic = new Demographic("test id", new DemographicUser(), CATEGORY_NAME, true, ImmutableList.of(), null);
        config = DemographicValuesValidationConfig.create();
    }

    @Test
    public void supports() {
        assertTrue(
                DemographicValuesValidationConfigValidator.INSTANCE.supports(DemographicValuesValidationConfig.class));
    }

    @Test
    public void validEnum() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"" +
                "    ]" +
                "}", JsonNode.class));
        Validate.entityThrowingException(DemographicValuesValidationConfigValidator.INSTANCE, config);
    }

    @Test
    public void validNumberRange() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -50000," +
                "    \"max\": 48268.3" +
                "}", JsonNode.class));
        Validate.entityThrowingException(DemographicValuesValidationConfigValidator.INSTANCE, config);
    }

    // should not happen, controller should catch null config
    @Test(expectedExceptions = NullPointerException.class)
    public void nullConfiguration() throws IOException {
        Validate.entityThrowingException(DemographicValuesValidationConfigValidator.INSTANCE, null);
    }

    @Test
    public void nullType() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "    ]" +
                "}", JsonNode.class));
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationType",
                CANNOT_BE_NULL);
    }

    @Test
    public void nullRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                CANNOT_BE_NULL);
    }

    @Test
    public void nullNodeRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().nullNode());
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                CANNOT_BE_NULL);
    }

    @Test
    public void nullTypeAndRules() throws IOException {
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationType",
                CANNOT_BE_NULL);
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                CANNOT_BE_NULL);
    }

    @Test
    public void rules_enum_IOException() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                INVALID_VALIDATION_RULES);
    }

    @Test
    public void rules_enum_invalidLanguageCode() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"abc\": [" +
                "        \"foo\"" +
                "    ]" +
                "}", JsonNode.class));
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config,
                "validationRules.languageCode", INVALID_CONFIGURATION_BAD_LANGUAGE_CODE);
    }

    @Test
    public void rules_enum_wrongTypeAllowedValues() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        []" +
                "    ]" +
                "}", JsonNode.class));
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                INVALID_VALIDATION_RULES);

    }

    @Test
    public void rules_numberRange_IOException() throws IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        demographic.setValues(ImmutableList.of(
                new DemographicValue("5")));
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                INVALID_VALIDATION_RULES);
    }

    @Test
    public void rules_numberRange_wrongTypeMinMax() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": []" +
                "}", JsonNode.class));
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules",
                INVALID_VALIDATION_RULES);
    }

    @Test
    public void rules_minGreaterThanMax() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": 20," +
                "    \"max\": -20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("0")));
        assertValidatorMessage(DemographicValuesValidationConfigValidator.INSTANCE, config, "validationRules.minAndMax",
                INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX);
    }
}
