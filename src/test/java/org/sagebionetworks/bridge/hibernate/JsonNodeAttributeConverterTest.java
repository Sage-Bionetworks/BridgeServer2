package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.IOException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonNodeAttributeConverterTest {

    private static final String JSON = TestUtils.createJson("{'test':100}");
    private static JsonNode node;
    
    private static JsonNodeAttributeConverter converter;
    
    @BeforeMethod
    public void before() throws IOException {
        converter = new JsonNodeAttributeConverter();
        node = new ObjectMapper().readTree(JSON);
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(converter.convertToDatabaseColumn(node), JSON);
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(converter.convertToEntityAttribute(JSON), node);
    }
    
    @Test
    public void convertToDatabaseColumnNullsafe() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    public void convertToEntityAttributeNullsafe() {
        assertNull(converter.convertToEntityAttribute(null));
    }
}
