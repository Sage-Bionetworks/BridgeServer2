package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StringSetConverterTest {

    static final StringSetConverter CONVERTER = new StringSetConverter();
    
    @Test
    public void convertToDatabaseColumn() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(USER_DATA_GROUPS);
        
        Set<String> deser = BridgeObjectMapper.get().readValue(json, new TypeReference<Set<String>>() {});
        assertEquals(deser, USER_DATA_GROUPS);
    }

    @Test
    public void convertToDatabaseColumnNull() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }
    
    @Test
    public void convertToEntityAttribute() {
        String json = CONVERTER.convertToDatabaseColumn(USER_DATA_GROUPS);
        
        Set<String> deser = CONVERTER.convertToEntityAttribute(json);
        assertEquals(deser, USER_DATA_GROUPS);
    }

    @Test
    public void convertToEntityAttributeNull() {
        Set<String> deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test
    public void convertToEntityAttributeBadJsonReturnsNull() {
        Set<String> deser = CONVERTER.convertToEntityAttribute("not json");
        assertNull(deser);
    }
}
