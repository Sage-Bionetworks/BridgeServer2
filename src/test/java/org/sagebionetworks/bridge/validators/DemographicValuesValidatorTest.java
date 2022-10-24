package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class DemographicValuesValidatorTest {
    private static final String CATEGORY_NAME = "category1";
    private static final String INVALID_CONFIGURATION = "invalid configuration for demographics validation";
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_MIN_LARGER_THAN_MAX = "invalid min (cannot be larger than specified max)";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number value (not an acceptable number; consult the documentation to see what numbers are valid)";
    private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than specified min)";
    private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number value (greater than specified max)";

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
                "        \"-12\"," +
                "        \"\"" +
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
                new DemographicValue(""),
                new DemographicValue(new BigDecimal("1.7")),
                new DemographicValue(new BigDecimal("-12"))));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void enum_IOException() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        assertValidatorMessage(validator, demographic, "demographicsValidationConfiguration[" + CATEGORY_NAME + "]",
                INVALID_CONFIGURATION);
    }

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
    public void enum_wrongTypeAllowedValues() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        []" +
                "    ]" +
                "}", JsonNode.class));
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
                new DemographicValue(new BigDecimal("1.7")),
                new DemographicValue(new BigDecimal("-12"))));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void enum_noAllowedValuesSpecified() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue(new BigDecimal("1.7")),
                new DemographicValue(new BigDecimal("-12"))));
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
                new DemographicValue(new BigDecimal("1.7")),
                new DemographicValue(new BigDecimal("-12"))));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][1]",
                INVALID_ENUM_VALUE);
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][2]",
                INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_someInvalid() throws JsonMappingException, JsonProcessingException {
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
                new DemographicValue(new BigDecimal("1.7")),
                new DemographicValue(new BigDecimal("-12"))));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_ENUM_VALUE);
    }

    @Test
    public void numberRange_validNoMinNoMax() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createObjectNode());
        demographic.setValues(ImmutableList.of(
                // with repetition
                new DemographicValue(new BigDecimal("5")),
                new DemographicValue(new BigDecimal("5")),
                // positive and negative
                // float and int
                new DemographicValue(new BigDecimal("10.2")),
                new DemographicValue(new BigDecimal("-2")),
                new DemographicValue(new BigDecimal("-7.8")),
                new DemographicValue("1.7e308"),
                new DemographicValue("-1.7e308")));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_valid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -50000," +
                "    \"max\": 48268.3" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                // repetition
                new DemographicValue(new BigDecimal("-50000")),
                new DemographicValue(new BigDecimal("-50000")),
                // close but not quite
                new DemographicValue(new BigDecimal("-49999.9999999999999999")),
                new DemographicValue(new BigDecimal("48268.29999999999999")),
                // all combinations with +/-, exponent/non-exponent (capitalized and
                // un-capitalized) with +/- exponent
                // 0
                new DemographicValue(new BigDecimal(0)),
                new DemographicValue("0"),
                new DemographicValue("0e20"),
                new DemographicValue("0E20"),
                new DemographicValue("0e-20"),
                new DemographicValue("0E-20"),
                new DemographicValue("0e+20"),
                new DemographicValue("0E+20"),
                new DemographicValue("+0"),
                new DemographicValue("+0e20"),
                new DemographicValue("+0E20"),
                new DemographicValue("+0e-20"),
                new DemographicValue("+0E-20"),
                new DemographicValue("+0e+20"),
                new DemographicValue("+0E+20"),
                new DemographicValue("-0"),
                new DemographicValue("-0e20"),
                new DemographicValue("-0E20"),
                new DemographicValue("-0e-20"),
                new DemographicValue("-0E-20"),
                new DemographicValue("-0e+20"),
                new DemographicValue("-0E+20"),
                // int
                new DemographicValue(new BigDecimal("482")),
                new DemographicValue("482"),
                new DemographicValue(new BigDecimal("482e2")),
                new DemographicValue("482e2"),
                new DemographicValue("482E2"),
                new DemographicValue("482e+2"),
                new DemographicValue("482E+2"),
                new DemographicValue(new BigDecimal("482e-7")),
                new DemographicValue("482e-7"),
                new DemographicValue("482E-7"),
                new DemographicValue("+482"),
                new DemographicValue("+482e2"),
                new DemographicValue("+482E2"),
                new DemographicValue("+482e+2"),
                new DemographicValue("+482E+2"),
                new DemographicValue("+482e-7"),
                new DemographicValue("+482E-7"),
                new DemographicValue(new BigDecimal("-482")),
                new DemographicValue("-482"),
                new DemographicValue(new BigDecimal("-482e2")),
                new DemographicValue("-482e2"),
                new DemographicValue("-482E2"),
                new DemographicValue("-482e+2"),
                new DemographicValue("-482E+2"),
                new DemographicValue(new BigDecimal("-482e-7")),
                new DemographicValue("-482e-7"),
                new DemographicValue("-482E-7"),
                // int with decimal
                new DemographicValue("482."),
                new DemographicValue("482.e2"),
                new DemographicValue("482.E2"),
                new DemographicValue("482.e+2"),
                new DemographicValue("482.E+2"),
                new DemographicValue("482.e-7"),
                new DemographicValue("482.E-7"),
                new DemographicValue("+482."),
                new DemographicValue("+482.e2"),
                new DemographicValue("+482.E2"),
                new DemographicValue("+482.e+2"),
                new DemographicValue("+482.E+2"),
                new DemographicValue("+482.e-7"),
                new DemographicValue("+482.E-7"),
                new DemographicValue("-482."),
                new DemographicValue("-482.e2"),
                new DemographicValue("-482.E2"),
                new DemographicValue("-482.e+2"),
                new DemographicValue("-482.E+2"),
                new DemographicValue("-482.e-7"),
                new DemographicValue("-482.E-7"),
                // int with decimal and fraction part
                new DemographicValue(new BigDecimal("482.683")),
                new DemographicValue("482.683"),
                new DemographicValue(new BigDecimal("482.683e2")),
                new DemographicValue("482.683e2"),
                new DemographicValue("482.683E2"),
                new DemographicValue("482.683e+2"),
                new DemographicValue("482.683E+2"),
                new DemographicValue(new BigDecimal("482.683e-7")),
                new DemographicValue("482.683e-7"),
                new DemographicValue("482.683E-7"),
                new DemographicValue("+482.683"),
                new DemographicValue("+482.683e2"),
                new DemographicValue("+482.683E2"),
                new DemographicValue("+482.683e+2"),
                new DemographicValue("+482.683E+2"),
                new DemographicValue("+482.683e-7"),
                new DemographicValue("+482.683E-7"),
                new DemographicValue(new BigDecimal("-482.683")),
                new DemographicValue("-482.683"),
                new DemographicValue(new BigDecimal("-482.683e2")),
                new DemographicValue("-482.683e2"),
                new DemographicValue("-482.683E2"),
                new DemographicValue("-482.683e+2"),
                new DemographicValue("-482.683E+2"),
                new DemographicValue(new BigDecimal("-482.683e-7")),
                new DemographicValue("-482.683e-7"),
                new DemographicValue("-482.683E-7"),
                // fraction part only
                new DemographicValue(new BigDecimal(".683")),
                new DemographicValue(".683"),
                new DemographicValue(new BigDecimal(".683e2")),
                new DemographicValue(".683e2"),
                new DemographicValue(".683E2"),
                new DemographicValue(".683e+2"),
                new DemographicValue(".683E+2"),
                new DemographicValue(new BigDecimal(".683e-2")),
                new DemographicValue(".683e-2"),
                new DemographicValue(".683E-2"),
                new DemographicValue("+.683"),
                new DemographicValue("+.683e2"),
                new DemographicValue("+.683E2"),
                new DemographicValue("+.683e+2"),
                new DemographicValue("+.683E+2"),
                new DemographicValue("+.683e-2"),
                new DemographicValue("+.683E-2"),
                new DemographicValue(new BigDecimal("-.683")),
                new DemographicValue("-.683"),
                new DemographicValue(new BigDecimal("-.683e2")),
                new DemographicValue("-.683e2"),
                new DemographicValue("-.683E2"),
                new DemographicValue("-.683e+2"),
                new DemographicValue("-.683E+2"),
                new DemographicValue(new BigDecimal("-.683e-2")),
                new DemographicValue("-.683e-2"),
                new DemographicValue("-.683E-2")));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_validNoMin() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"max\": 482.683" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                // repetition
                new DemographicValue(new BigDecimal("-999999999999999999999.")),
                new DemographicValue(new BigDecimal("-999999999999999999999.")),
                // out of range int but parsed as double
                new DemographicValue("-999999999999999999999"),
                new DemographicValue("-1.7e308"),
                new DemographicValue(new BigDecimal("482.683")),
                new DemographicValue("482.683"),
                new DemographicValue(new BigDecimal("0")),
                new DemographicValue(new BigDecimal("482.6829999999999"))));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_validNoMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -500" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                // repetition
                new DemographicValue(new BigDecimal("-500")),
                new DemographicValue(new BigDecimal("-500")),
                new DemographicValue("-500"),
                new DemographicValue(new BigDecimal("999999999999999999999.")),
                // out of range int but parsed as double
                new DemographicValue("999999999999999999999"),
                new DemographicValue("1.7e308"),
                new DemographicValue(new BigDecimal("0")),
                new DemographicValue(new BigDecimal("999999999999999999999.9999999999999"))));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_IOException() {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        assertValidatorMessage(validator, demographic, "demographicsValidationConfiguration[" + CATEGORY_NAME + "]",
                INVALID_CONFIGURATION);
    }

    @Test
    public void numberRange_IllegalArgumentException() {
        DemographicValuesValidationConfiguration config = mock(DemographicValuesValidationConfiguration.class);
        when(config.getValidationType())
                .thenReturn(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
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
    public void numberRange_wrongTypeMinMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": []" +
                "}", JsonNode.class));
        assertValidatorMessage(validator, demographic, "demographicsValidationConfiguration[" + CATEGORY_NAME + "]",
                INVALID_CONFIGURATION);
    }

    @Test
    public void numberRange_invalidFields() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"foo\": \"bar\"" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue(new BigDecimal("5"))));
        // unknown fields should be ignored by BridgeObjectMapper
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_valueEmpty() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_decimalOnly() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue(".")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_notDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("foo")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_exponentNoDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5e")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_exponentSignNoDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5e-")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_signNoDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("-")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_nanString() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("NaN")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_infinityString() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("Infinity")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_hex() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("0xa")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void minGreaterThanMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": 20," +
                "    \"max\": -20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("0")));
        assertValidatorMessage(validator, demographic, "demographicsValidationConfiguration[" + CATEGORY_NAME + "].min",
                INVALID_MIN_LARGER_THAN_MAX);
    }

    @Test
    public void minEqualToMax() throws JsonMappingException, JsonProcessingException {
        // allowed because range check is inclusive

        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": 20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("20")));
        Validate.entityThrowingException(validator, demographic);
    }

    @Test
    public void numberRange_lessThanMin() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("-40")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_LESS_THAN_MIN);
    }

    @Test
    public void numberRange_moreThanMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationConfiguration.ValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("40")));
        assertValidatorMessage(validator, demographic, "demographics[" + CATEGORY_NAME + "][0]",
                INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
    }
}
