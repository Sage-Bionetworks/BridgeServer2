package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.StringUtils;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class DemographicValuesValidatorTest {
    private static final String CATEGORY_NAME = "category1";
    private static final String INVALID_CONFIGURATION = "invalid configuration for demographics validation";
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number value (not an acceptable number; consult the documentation to see what numbers are valid)";
    private static final String INVALID_NUMBER_VALUE_MIN = "invalid number value (less than specified min)";
    private static final String INVALID_NUMBER_VALUE_MAX = "invalid number value (greater than specified max)";

    private Demographic demographic;
    private DemographicValuesValidationConfiguration config;
    private DemographicValuesValidator validator;

    @BeforeMethod
    public void beforeMethod() {
        demographic = new Demographic("test id", new DemographicUser(), CATEGORY_NAME, true, ImmutableList.of(), null);
        config = new DemographicValuesValidationConfiguration();
        validator = new DemographicValuesValidator(config);
    }

    @Test
    public void supports_nullConfig() {
        assertFalse(new DemographicValuesValidator(null).supports(Demographic.class));
    }

    @Test
    public void supports_nonNullConfig() {
        assertTrue(new DemographicValuesValidator(new DemographicValuesValidationConfiguration())
                .supports(Demographic.class));
    }

    @Test
    public void callsDemographicValuesValidationConfigurationValidator() {
        assertValidatorMessage(validator, demographic,
                "demographicsValidationConfiguration[" + CATEGORY_NAME + "].validationType", CANNOT_BE_NULL);
    }

    @Test
    public void enum_valid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"" +
                "    ]," +
                // another language, should be ignored
                "    \"sp\": [" +
                "        \"baz\"," +
                "        \"qux\"" +
                "    ]" +
                "}", JsonNode.class));
        // with repetition
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("foo"),
                new DemographicValue(1.7),
                new DemographicValue(-12)));
        Validate.entityThrowingException(validator, demographic);
    }

    // IOException for the same catch is tested in enum_wrongTypeRules
    @Test
    public void enum_IllegalArgumentException() {
        DemographicValuesValidationConfiguration config = mock(DemographicValuesValidationConfiguration.class);
        when(config.getValidationType()).thenReturn(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        // first call in configuration validator succeeds, second call in values
        // validator fails
        // (simulates exception when converting enum rules)
        when(config.getValidationRules()).thenReturn(BridgeObjectMapper.get().createObjectNode())
                .thenThrow(new IllegalArgumentException());
        validator = new DemographicValuesValidator(config);
        assertValidatorMessage(validator, demographic, "demographicsValidationConfiguration[" + CATEGORY_NAME + "]",
                INVALID_CONFIGURATION);
    }

    @Test
    public void enum_wrongTypeRules() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        assertValidatorMessage(validator, demographic, "demographicsValidationConfiguration[" + CATEGORY_NAME + "]",
                INVALID_CONFIGURATION);
    }

    @Test
    public void enum_emptyRules() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createObjectNode());
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void enum_nonStringAllowedValues() throws JsonMappingException, JsonProcessingException {
        // will not error, they will just be converted to strings
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"," +
                "        1.7," +
                "        -12," +
                "        null" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue(1.7),
                new DemographicValue(-12)));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void enum_noAllowedValuesSpecified() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue(1.7),
                new DemographicValue(-12)));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 0 + "]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 1 + "]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 2 + "]",
                INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_noErrorWhenNoEnglish() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"sp\": [" +
                "        \"foo\"," +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"" +
                "    ]" +
                "}", JsonNode.class));
        // ignored because only "en" is checked right now
        demographic.setValues(ImmutableList.of(new DemographicValue("abc")));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void enum_allInvalid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"abc\"," +
                "        \"def\"" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue(1.7),
                new DemographicValue(-12)));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 0 + "]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 1 + "]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 2 + "]",
                INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_someInvalid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue(1.7),
                new DemographicValue(-12)));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 1 + "]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][" + 2 + "]",
                INVALID_ENUM_VALUE);
    }

    @Test
    public void numberRange_valid() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_validNoMinNoMax() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createObjectNode());
        demographic.setValues(ImmutableList.of(
                // with repetition
                new DemographicValue(5),
                new DemographicValue(5),
                // positive and negative
                // float and int
                new DemographicValue(10.2),
                new DemographicValue(-2),
                new DemographicValue(-7.8),
                // up to 300 digits before decimal
                new DemographicValue(StringUtils.repeat("9", 300)),
                // without 0 before decimal point for numbers <1 and >-1
                new DemographicValue(".142857"),
                // without no digits after decimal point
                new DemographicValue("8.")));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_validNoMin() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_validNoMax() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_IOException() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_IllegalArgumentException() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_wrongTypeMinMax() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_emptyRules() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void invalidRules() {

    }

    @Test
    public void numberRange_notANumber() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_tooManyDigits() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_empty() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_nan() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_inf() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_negInf() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_hex() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_lessThanMin() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }

    @Test
    public void numberRange_moreThanMax() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);

    }
}
