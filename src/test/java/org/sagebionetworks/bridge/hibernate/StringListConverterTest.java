package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import javax.persistence.PersistenceException;

import com.fasterxml.jackson.core.type.TypeReference;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StringListConverterTest extends Mockito {

    static final StringListConverter CONVERTER = new StringListConverter();
    
    @Test
    public void convertToDatabaseColumn() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(LANGUAGES);
        
        List<String> deser = BridgeObjectMapper.get().readValue(json, new TypeReference<List<String>>() {});
        assertEquals(deser, LANGUAGES);
    }

    @Test
    public void convertToDatabaseColumnNull() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }
    
    @Test
    public void convertToEntityAttribute() {
        String json = CONVERTER.convertToDatabaseColumn(LANGUAGES);
        
        List<String> deser = CONVERTER.convertToEntityAttribute(json);
        assertEquals(deser, LANGUAGES);
    }

    @Test
    public void convertToEntityAttributeNull() {
        List<String> deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void convertToEntityAttributeBadJsonThrows() {
        List<String> deser = CONVERTER.convertToEntityAttribute("not json");
        assertNull(deser);
    }
}
