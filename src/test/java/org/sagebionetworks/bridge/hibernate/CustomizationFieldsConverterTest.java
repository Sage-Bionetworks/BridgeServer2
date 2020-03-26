package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CUSTOMIZATION_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.INFO1;
import static org.sagebionetworks.bridge.TestConstants.INFO2;
import static org.sagebionetworks.bridge.TestConstants.INFO3;
import static org.sagebionetworks.bridge.TestConstants.INFO4;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.assessments.PropertyInfo;

public class CustomizationFieldsConverterTest extends Mockito {
    
    private static final String SER_VALUE = "{" + 
            "\"guid1\":[" + 
                "{" + 
                    "\"propName\":\"foo\"," + 
                    "\"label\":\"foo label\"," + 
                    "\"description\":\"a description\"," + 
                    "\"propType\":\"string\"," + 
                    "\"type\":\"PropertyInfo\"" +
                "}," + 
                "{" + 
                    "\"propName\":\"bar\"," + 
                    "\"label\":\"bar label\"," + 
                    "\"description\":\"a description\"," + 
                    "\"propType\":\"string\"," + 
                    "\"type\":\"PropertyInfo\"" +
                "}" + 
            "]," + 
            "\"guid2\":[" + 
                "{" + 
                    "\"propName\":\"baz\"," + 
                    "\"label\":\"baz label\"," + 
                    "\"description\":\"a description\"," + 
                    "\"propType\":\"string\"," + 
                    "\"type\":\"PropertyInfo\"" +
                "}," + 
                "{" + 
                    "\"propName\":\"bop\"," + 
                    "\"label\":\"bop label\"," + 
                    "\"description\":\"a description\"," + 
                    "\"propType\":\"string\"," + 
                    "\"type\":\"PropertyInfo\"" +
                "}" + 
            "]" + 
        "}";
    
    @Spy
    private CustomizationFieldsConverter converter;
    
    @Mock
    ObjectMapper mockMapper;
    
    @Mock
    JsonProcessingException mockJsonException;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void serializeNull() {
        String ser = converter.convertToDatabaseColumn(null);
        assertNull(ser);
    }
    
    @Test
    public void serializeEmpty() {
        String ser = converter.convertToDatabaseColumn(new HashMap<>());
        assertEquals(ser, "{}");
    }
    
    @Test
    public void serializeValue() {
        String ser = converter.convertToDatabaseColumn(CUSTOMIZATION_FIELDS);
        assertEquals(ser, SER_VALUE);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void serializeInvalidValue() throws Exception {
        when(mockMapper.writeValueAsString(any())).thenThrow(mockJsonException);
        when(converter.getObjectMapper()).thenReturn(mockMapper);
        
        Map<String,Set<PropertyInfo>> map = new HashMap<>();
        converter.convertToDatabaseColumn(map);
    }
    
    @Test
    public void deserializeNull() {
        Map<String, Set<PropertyInfo>> map = converter.convertToEntityAttribute(null);
        assertNull(map);
    }

    @Test
    public void deserialize() {
        Map<String, Set<PropertyInfo>> map = converter.convertToEntityAttribute(SER_VALUE);
        
        assertEquals(map.size(), 2);
        Set<PropertyInfo> set1 = map.get("guid1");
        assertEquals(set1, ImmutableSet.of(INFO1, INFO2));
        
        Set<PropertyInfo> set2 = map.get("guid2");
        assertEquals(set2, ImmutableSet.of(INFO3, INFO4));
    }
    
    @Test(expectedExceptions = PersistenceException.class)
    public void deserializeInvalidValue() {
        converter.convertToEntityAttribute("{");
    }
}
