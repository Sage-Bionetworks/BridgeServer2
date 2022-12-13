package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class DemographicValuesValidatorTest {
    private static final String CATEGORY_NAME = "category1";
    private static final String INVALID_DATA = "invalid data";
    private static final String INVALID_CONFIGURATION = INVALID_DATA;
    private static final String INVALID_CONFIGURATION_BAD_LANGUAGE_CODE = INVALID_CONFIGURATION;
    private static final String INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX = INVALID_CONFIGURATION;
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number";
    private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than min)";
    private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number (larger than max)";

    private Demographic demographic;
    private DemographicValuesValidationConfig config;
    private DemographicValuesValidator validator;

    @BeforeMethod
    public void beforeMethod() {
        demographic = new Demographic("test id", new DemographicUser(), CATEGORY_NAME, true, ImmutableList.of(), null);
        config = DemographicValuesValidationConfig.create();
        validator = new DemographicValuesValidator(config);
    }

    private void assertAllValuesInvalidity(Demographic demographic, String invalidity) {
        for (DemographicValue value : demographic.getValues()) {
            assertEquals(value.getInvalidity(), invalidity);
        }
    }

    private void assertOneValueInvalidity(Demographic demographic, int index, String invalidity) {
        assertEquals(demographic.getValues().get(index).getInvalidity(), invalidity);
    }

    private void assertNoValuesInvalid(Demographic demographic) {
        for (DemographicValue value : demographic.getValues()) {
            assertNull(value.getInvalidity());
        }
    }

    @Test
    public void nullType() throws JsonMappingException, JsonProcessingException {
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "    ]" +
                "}", JsonNode.class));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void nullRules() {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void nullNodeRules() {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().nullNode());
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void nullTypeAndRules() {
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void nullConfiguration() {
        validator = new DemographicValuesValidator(null);
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void enum_valid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"foo\"," +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"," +
                "        \"\"" +
                "    ]," +
                // another language, should be ignored
                "    \"es\": [" +
                "        \"baz\"," +
                "        \"qux\"" +
                "    ]" +
                "}", JsonNode.class));
        // with repetition
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("foo"),
                new DemographicValue(""),
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void enum_IOException() {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void enum_invalidLanguageCode() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"abc\": [" +
                "        \"foo\"" +
                "    ]" +
                "}", JsonNode.class));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION_BAD_LANGUAGE_CODE);
    }

    @Test
    public void enum_wrongTypeAllowedValues() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        []" +
                "    ]" +
                "}", JsonNode.class));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void enum_emptyRules() {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createObjectNode());
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void enum_nonStringAllowedValues() throws JsonMappingException, JsonProcessingException {
        // will not error, they will just be converted to strings
        config.setValidationType(DemographicValuesValidationType.ENUM);
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
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    // technically the same case as enum_allInvalid
    @Test
    public void enum_noAllowedValuesSpecified() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_noErrorWhenNoEnglish() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"es\": [" +
                "        \"foo\"," +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"" +
                "    ]" +
                "}", JsonNode.class));
        // ignored because only "en" is checked right now
        demographic.setValues(ImmutableList.of(new DemographicValue("abc")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void enum_allInvalid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"abc\"," +
                "        \"def\"" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_someInvalid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "        \"bar\"," +
                "        \"1.7\"," +
                "        \"-12\"" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_ENUM_VALUE);
    }

    @Test
    public void numberRange_validNoMinNoMax() {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createObjectNode());
        demographic.setValues(ImmutableList.of(
                // with repetition
                new DemographicValue("5"),
                new DemographicValue("5"),
                // positive and negative
                // float and int
                new DemographicValue("10.2"),
                new DemographicValue("-2"),
                new DemographicValue("-7.8"),
                new DemographicValue("1.7e308"),
                new DemographicValue("-1.7e308")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_valid() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -50000," +
                "    \"max\": 48268.3" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                // repetition
                new DemographicValue("-50000"),
                new DemographicValue("-50000"),
                // close but not quite
                new DemographicValue("-49999.9999999999999999"),
                new DemographicValue("48268.29999999999999"),
                // all combinations with +/-, exponent/non-exponent (capitalized and
                // un-capitalized) with +/- exponent
                // 0
                new DemographicValue("0"),
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
                new DemographicValue("482"),
                new DemographicValue("482"),
                new DemographicValue("482e2"),
                new DemographicValue("482e2"),
                new DemographicValue("482E2"),
                new DemographicValue("482e+2"),
                new DemographicValue("482E+2"),
                new DemographicValue("482e-7"),
                new DemographicValue("482e-7"),
                new DemographicValue("482E-7"),
                new DemographicValue("+482"),
                new DemographicValue("+482e2"),
                new DemographicValue("+482E2"),
                new DemographicValue("+482e+2"),
                new DemographicValue("+482E+2"),
                new DemographicValue("+482e-7"),
                new DemographicValue("+482E-7"),
                new DemographicValue("-482"),
                new DemographicValue("-482"),
                new DemographicValue("-482e2"),
                new DemographicValue("-482e2"),
                new DemographicValue("-482E2"),
                new DemographicValue("-482e+2"),
                new DemographicValue("-482E+2"),
                new DemographicValue("-482e-7"),
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
                new DemographicValue("482.683"),
                new DemographicValue("482.683"),
                new DemographicValue("482.683e2"),
                new DemographicValue("482.683e2"),
                new DemographicValue("482.683E2"),
                new DemographicValue("482.683e+2"),
                new DemographicValue("482.683E+2"),
                new DemographicValue("482.683e-7"),
                new DemographicValue("482.683e-7"),
                new DemographicValue("482.683E-7"),
                new DemographicValue("+482.683"),
                new DemographicValue("+482.683e2"),
                new DemographicValue("+482.683E2"),
                new DemographicValue("+482.683e+2"),
                new DemographicValue("+482.683E+2"),
                new DemographicValue("+482.683e-7"),
                new DemographicValue("+482.683E-7"),
                new DemographicValue("-482.683"),
                new DemographicValue("-482.683"),
                new DemographicValue("-482.683e2"),
                new DemographicValue("-482.683e2"),
                new DemographicValue("-482.683E2"),
                new DemographicValue("-482.683e+2"),
                new DemographicValue("-482.683E+2"),
                new DemographicValue("-482.683e-7"),
                new DemographicValue("-482.683e-7"),
                new DemographicValue("-482.683E-7"),
                // fraction part only
                new DemographicValue(".683"),
                new DemographicValue(".683"),
                new DemographicValue(".683e2"),
                new DemographicValue(".683e2"),
                new DemographicValue(".683E2"),
                new DemographicValue(".683e+2"),
                new DemographicValue(".683E+2"),
                new DemographicValue(".683e-2"),
                new DemographicValue(".683e-2"),
                new DemographicValue(".683E-2"),
                new DemographicValue("+.683"),
                new DemographicValue("+.683e2"),
                new DemographicValue("+.683E2"),
                new DemographicValue("+.683e+2"),
                new DemographicValue("+.683E+2"),
                new DemographicValue("+.683e-2"),
                new DemographicValue("+.683E-2"),
                new DemographicValue("-.683"),
                new DemographicValue("-.683"),
                new DemographicValue("-.683e2"),
                new DemographicValue("-.683e2"),
                new DemographicValue("-.683E2"),
                new DemographicValue("-.683e+2"),
                new DemographicValue("-.683E+2"),
                new DemographicValue("-.683e-2"),
                new DemographicValue("-.683e-2"),
                new DemographicValue("-.683E-2")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_validNoMin() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"max\": 482.683" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                // repetition
                new DemographicValue("-999999999999999999999."),
                new DemographicValue("-999999999999999999999."),
                // out of range int but parsed as double
                new DemographicValue("-999999999999999999999"),
                new DemographicValue("-1.7e308"),
                new DemographicValue("482.683"),
                new DemographicValue("482.683"),
                new DemographicValue("0"),
                new DemographicValue("482.6829999999999")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_validNoMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -500" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                // repetition
                new DemographicValue("-500"),
                new DemographicValue("-500"),
                new DemographicValue("-500"),
                new DemographicValue("999999999999999999999."),
                // out of range int but parsed as double
                new DemographicValue("999999999999999999999"),
                new DemographicValue("1.7e308"),
                new DemographicValue("0"),
                new DemographicValue("999999999999999999999.9999999999999")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_IOException() {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        demographic.setValues(ImmutableList.of(
                new DemographicValue("5")));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void numberRange_wrongTypeMinMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": []" +
                "}", JsonNode.class));
        validator.validate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_CONFIGURATION);
    }

    @Test
    public void numberRange_invalidFields() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"foo\": \"bar\"" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5")));
        // unknown fields should be ignored by BridgeObjectMapper
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_valueEmpty() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_decimalOnly() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue(".")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_notDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("foo")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_exponentNoDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5e")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_exponentSignNoDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5e-")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_signNoDigits() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("-")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_nanString() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("NaN")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_infinityString() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("Infinity")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_hex() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("0xa")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void minGreaterThanMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": 20," +
                "    \"max\": -20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("0")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX);
    }

    @Test
    public void minEqualToMax() throws JsonMappingException, JsonProcessingException {
        // allowed because range check is inclusive

        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": 20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("20")));
        validator.validate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_lessThanMin() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("-40")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_LESS_THAN_MIN);
    }

    @Test
    public void numberRange_moreThanMax() throws JsonMappingException, JsonProcessingException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("40")));
        validator.validate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
    }
}
