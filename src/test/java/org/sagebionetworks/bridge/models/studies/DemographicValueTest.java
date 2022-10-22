package org.sagebionetworks.bridge.models.studies;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class DemographicValueTest {
    @Test
    public void string() {
        DemographicValue value = new DemographicValue("foo");
        assertEquals(value.getValue(), "foo");
    }

    @Test
    public void stringNull() {
        DemographicValue value = new DemographicValue(null);
        assertEquals(value.getValue(), null);
    }

    @Test
    public void longZero() {
        DemographicValue value = new DemographicValue(0L);
        assertEquals(value.getValue(), "0");
    }

    @Test
    public void longPos() {
        DemographicValue value = new DemographicValue(123456789L);
        assertEquals(value.getValue(), "123456789");
    }

    @Test
    public void longNeg() {
        DemographicValue value = new DemographicValue(-123456789L);
        assertEquals(value.getValue(), "-123456789");
    }

    @Test
    public void doubleZero() {
        DemographicValue value = new DemographicValue(0d);
        assertEquals(value.getValue(), "0.0");
    }

    @Test
    public void doublePos() {
        DemographicValue value = new DemographicValue(123456d);
        assertEquals(value.getValue(), "123456.0");
    }

    @Test
    public void doubleNeg() {
        DemographicValue value = new DemographicValue(-123456d);
        assertEquals(value.getValue(), "-123456.0");
    }

    @Test
    public void doublePosDecimal() {
        DemographicValue value = new DemographicValue(123456.789d);
        assertEquals(value.getValue(), "123456.789");
    }

    @Test
    public void doubleNegDecimal() {
        DemographicValue value = new DemographicValue(-123456.789d);
        assertEquals(value.getValue(), "-123456.789");
    }

    @Test
    public void doublePosLarge() {
        DemographicValue value = new DemographicValue(1.23456789e15);
        assertEquals(value.getValue(), "1.23456789E15");
    }

    @Test
    public void doubleNegLarge() {
        DemographicValue value = new DemographicValue(-1.23456789e15);
        assertEquals(value.getValue(), "-1.23456789E15");
    }

    @Test
    public void doublePosSmall() {
        DemographicValue value = new DemographicValue(1.23456789e-15);
        assertEquals(value.getValue(), "1.23456789E-15");
    }

    @Test
    public void doubleNegSmall() {
        DemographicValue value = new DemographicValue(-1.23456789e-15);
        assertEquals(value.getValue(), "-1.23456789E-15");
    }

    @Test
    public void booleanTrue() {
        DemographicValue value = new DemographicValue(true);
        assertEquals(value.getValue(), "true");
    }

    @Test
    public void booleanFalse() {
        DemographicValue value = new DemographicValue(false);
        assertEquals(value.getValue(), "false");
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
