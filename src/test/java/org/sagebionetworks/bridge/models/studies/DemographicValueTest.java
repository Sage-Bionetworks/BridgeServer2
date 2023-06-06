package org.sagebionetworks.bridge.models.studies;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.testng.annotations.Test;

// JSON tests are located in DemographicValueDeserializerTest because DemographicValue uses a custom deserializer
public class DemographicValueTest {
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
