package org.sagebionetworks.bridge.models.demographics;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class DemographicValueTest {
    private void test(String jsonString, String expectedValue) throws JsonMappingException, JsonProcessingException {
        DemographicValue value = BridgeObjectMapper.get().readValue(jsonString, DemographicValue.class);
        if (value == null) {
            assertNull(expectedValue);
        } else {
            assertEquals(value.getValue(), expectedValue);
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
        test("0.12345678901234567890", "0.12345678901234567890");
    }

    @Test
    public void decimalNegFractionGreaterThanNegOne() throws JsonMappingException, JsonProcessingException {
        test("-0.12345678901234567890", "-0.12345678901234567890");
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
    public void keyValue() {
        DemographicValue value = new DemographicValue("foo", "bar");
        assertEquals(value.getValue(), "foo=bar");
    }

    @Test
    public void nullKeyValue() {
        DemographicValue value = new DemographicValue(null, "bar");
        assertEquals(value.getValue(), "null=bar");
    }

    @Test
    public void keyNullValue() {
        DemographicValue value = new DemographicValue("foo", null);
        assertEquals(value.getValue(), "foo=null");
    }

    @Test
    public void nullKeyNullValue() {
        DemographicValue value = new DemographicValue(null, null);
        assertEquals(value.getValue(), "null=null");
    }
}
