package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.IOException;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
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
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number";
    private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than min)";
    private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number (larger than max)";

    private Demographic demographic;
    private DemographicValuesValidationConfig config;

    @BeforeMethod
    public void beforeMethod() {
        demographic = new Demographic("test id", new DemographicUser(), CATEGORY_NAME, true, ImmutableList.of(), null);
        config = DemographicValuesValidationConfig.create();
    }

    private void tryValidate(Demographic demographic) throws IOException {
        DemographicValuesValidator validator = config.getValidationType()
                .getValidatorWithRules(config.getValidationRules());
        validator.validateDemographicUsingRules(demographic);
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

    // should not happen in practice because json string null is deserialized as JsonNode Null not java null
    @Test(expectedExceptions = InvalidEntityException.class)
    public void enum_nullRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        tryValidate(demographic);
    }

    // should not happen in practice because config validator should catch this case
    @Test(expectedExceptions = InvalidEntityException.class)
    public void enum_nullNodeRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().nullNode());
        tryValidate(demographic);
    }

    @Test
    public void enum_valid() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    // IOException should just be propagated, config validator should have caught deserialization error
    @Test(expectedExceptions = IOException.class)
    public void enum_IOException() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        tryValidate(demographic);
    }

    @Test
    public void enum_emptyRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().createObjectNode());
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void enum_nonStringAllowedValues() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    // technically the same case as enum_allInvalid
    @Test
    public void enum_noAllowedValuesSpecified() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.ENUM);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"en\": [" +
                "    ]" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(
                new DemographicValue("foo"),
                new DemographicValue("1.7"),
                new DemographicValue("-12")));
        tryValidate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_noErrorWhenNoEnglish() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void enum_allInvalid() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertAllValuesInvalidity(demographic, INVALID_ENUM_VALUE);
    }

    @Test
    public void enum_someInvalid() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_ENUM_VALUE);
    }

    // should not happen in practice because json string null is deserialized as JsonNode Null not java null
    @Test(expectedExceptions = InvalidEntityException.class)
    public void numberRange_nullRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        tryValidate(demographic);
    }

    // should not happen in practice because config validator should catch this case
    @Test(expectedExceptions = InvalidEntityException.class)
    public void numberRange_nullNodeRules() throws IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().nullNode());
        tryValidate(demographic);
    }

    @Test
    public void numberRange_validNoMinNoMax() throws IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_valid() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_validNoMin() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_validNoMax() throws JsonMappingException, JsonProcessingException, IOException {
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
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    // IOException should just be propagated, config validator should have caught deserialization error
    @Test(expectedExceptions = IOException.class)
    public void numberRange_IOException() throws IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().createArrayNode());
        demographic.setValues(ImmutableList.of(
                new DemographicValue("5")));
        tryValidate(demographic);
    }

    @Test
    public void numberRange_invalidFields() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"foo\": \"bar\"" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5")));
        // unknown fields should be ignored by BridgeObjectMapper
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_valueEmpty() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_decimalOnly() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue(".")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_notDigits() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("foo")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_exponentNoDigits()
            throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5e")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_exponentSignNoDigits()
            throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("5e-")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_signNoDigits()
            throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("-")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_nanString() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("NaN")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_infinityString()
            throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -10," +
                "    \"max\": 10" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("Infinity")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void numberRange_notANumber_hex() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("0xa")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_NOT_A_NUMBER);
    }

    @Test
    public void minEqualToMax() throws JsonMappingException, JsonProcessingException, IOException {
        // allowed because range check is inclusive

        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": 20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("20")));
        tryValidate(demographic);
        assertNoValuesInvalid(demographic);
    }

    @Test
    public void numberRange_lessThanMin() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("-40")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_LESS_THAN_MIN);
    }

    @Test
    public void numberRange_moreThanMax() throws JsonMappingException, JsonProcessingException, IOException {
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        config.setValidationRules(BridgeObjectMapper.get().readValue("{" +
                "    \"min\": -20," +
                "    \"max\": 20" +
                "}", JsonNode.class));
        demographic.setValues(ImmutableList.of(new DemographicValue("40")));
        tryValidate(demographic);
        assertOneValueInvalidity(demographic, 0, INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
    }

}
