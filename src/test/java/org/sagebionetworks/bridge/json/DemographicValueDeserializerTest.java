package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class DemographicValueDeserializerTest {
    private void test(String jsonString, String expectedValue) throws JsonMappingException, JsonProcessingException {
        test(jsonString, expectedValue, null);
    }

    private void test(String jsonString, String expectedValue, String expectedInvalidity) throws JsonMappingException, JsonProcessingException {
        DemographicValue value = BridgeObjectMapper.get().readValue(jsonString, DemographicValue.class);
        if (expectedValue == null) {
            assertNull(value);
        } else {
            assertEquals(value.getValue(), expectedValue);
        }
        if (value != null && expectedInvalidity != null) {
            assertEquals(value.getInvalidity(), expectedInvalidity);
        }
    }

    @Test
    public void string() throws JsonMappingException, JsonProcessingException {
        test("\"foo\"", "foo");
    }

    @Test
    public void stringNull() throws JsonMappingException, JsonProcessingException {
        test("null", null);
    }

    @Test
    public void intZero() throws JsonMappingException, JsonProcessingException {
        test("0", "0");
    }

    @Test
    public void intPos() throws JsonMappingException, JsonProcessingException {
        test("123456789", "123456789");
    }

    @Test
    public void intNeg() throws JsonMappingException, JsonProcessingException {
        test("-123456789", "-123456789");
    }

    @Test
    public void decimalZero() throws JsonMappingException, JsonProcessingException {
        test("0.0", "0.0");
    }

    @Test
    public void decimalPos() throws JsonMappingException, JsonProcessingException {
        test("123456.0", "123456.0");
    }

    @Test
    public void decimalNeg() throws JsonMappingException, JsonProcessingException {
        test("-123456.0", "-123456.0");
    }

    @Test
    public void decimalPosFraction() throws JsonMappingException, JsonProcessingException {
        test("123456.789", "123456.789");
    }

    @Test
    public void decimalNegFraction() throws JsonMappingException, JsonProcessingException {
        test("-123456.789", "-123456.789");
    }

    @Test
    public void decimalPosFractionLessThanOne() throws JsonMappingException, JsonProcessingException {
        test("0.1234567890123456", "0.1234567890123456");
    }

    @Test
    public void decimalNegFractionGreaterThanNegOne() throws JsonMappingException, JsonProcessingException {
        test("-0.1234567890123456", "-0.1234567890123456");
    }

    @Test
    public void decimalPosLarge() throws JsonMappingException, JsonProcessingException {
        test("123456789000000000000000", "123456789000000000000000");
    }

    @Test
    public void decimalNegLarge() throws JsonMappingException, JsonProcessingException {
        test("-123456789000000000000000", "-123456789000000000000000");
    }

    @Test
    public void decimalPosSmall() throws JsonMappingException, JsonProcessingException {
        test("0.000000000000000123456789", "1.23456789E-16");
    }

    @Test
    public void decimalNegSmall() throws JsonMappingException, JsonProcessingException {
        test("-0.000000000000000123456789", "-1.23456789E-16");
    }

    @Test
    public void booleanTrue() throws JsonMappingException, JsonProcessingException {
        test("true", "true");
    }

    @Test
    public void booleanFalse() throws JsonMappingException, JsonProcessingException {
        test("false", "false");
    }

    @Test
    public void objectNull() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": null}", "null");
    }

    @Test
    public void objectString() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": \"xyz\"}", "xyz");
    }

    @Test
    public void objectNumber() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": 3}", "3");
    }

    @Test
    public void objectDecimal() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": 3.14159}", "3.14159");
    }

    @Test
    public void objectBooleanTrue() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": true}", "true");
    }

    @Test
    public void objectBooleanFalse() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": false}", "false");
    }

    @Test
    public void objectInvalidNull() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": null, \"invalidity\": \"foo\"}", "null", "foo");
    }

    @Test
    public void objectInvalidString() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": \"xyz\", \"invalidity\": \"foo\"}", "xyz", "foo");
    }

    @Test
    public void objectInvalidNumber() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": 3, \"invalidity\": \"foo\"}", "3", "foo");
    }

    @Test
    public void objectInvalidDecimal() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": 3.14159, \"invalidity\": \"foo\"}", "3.14159", "foo");
    }

    @Test
    public void objectInvalidBooleanTrue() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": true, \"invalidity\": \"foo\"}", "true", "foo");
    }

    @Test
    public void objectInvalidBooleanFalse() throws JsonMappingException, JsonProcessingException {
        test("{\"value\": false, \"invalidity\": \"foo\"}", "false", "foo");
    }

    @Test(expectedExceptions = JsonProcessingException.class)
    public void array() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("[]", DemographicValue.class);
    }

    @Test(expectedExceptions = JsonProcessingException.class)
    public void objectArrayValue() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"value\": []}", DemographicValue.class);
    }

    @Test(expectedExceptions = JsonProcessingException.class)
    public void objectObjectValue() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"value\": {}}", DemographicValue.class);
    }
}
