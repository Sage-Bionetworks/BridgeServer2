package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

public class StringToStringSetMapConverterTest {

    private static final StringToStringSetMapConverter CONVERTER = new StringToStringSetMapConverter();
    private static final String SER_VALUE = "{\"key1\":[\"A\",\"B\",\"C\"],\"key2\":[\"D\",\"E\",\"F\"],\"key3\":[]}";
    
    @Test
    public void serializeNull() {
        String ser = CONVERTER.convertToDatabaseColumn(null);
        assertNull(ser);
    }
    
    @Test
    public void serializeEmpty() {
        String ser = CONVERTER.convertToDatabaseColumn(new HashMap<>());
        assertEquals(ser, "{}");
    }
    
    @Test
    public void serializeValue() {
        Set<String> set1 = ImmutableSet.of("A", "B", "C");
        Set<String> set2 = ImmutableSet.of("D", "E", "F");
        Set<String> set3 = ImmutableSet.of();
        Set<String> set4 = null;
        
        Map<String,Set<String>> map = new HashMap<>();
        map.put("key1", set1);
        map.put("key2", set2);
        map.put("key3", set3);
        map.put("key4", set4);
        
        String ser = CONVERTER.convertToDatabaseColumn(map);
        assertEquals(ser, SER_VALUE);
    }
    
    @Test
    public void deserializeNull() {
        Map<String, Set<String>> map = CONVERTER.convertToEntityAttribute(null);
        assertNull(map);
    }
    
    @Test
    public void deserialize() {
        Map<String, Set<String>> map = CONVERTER.convertToEntityAttribute(SER_VALUE);
        
        assertEquals(map.size(), 3);
        Set<String> set1 = map.get("key1");
        assertEquals(set1, ImmutableSet.of("A", "B", "C"));
        
        Set<String> set2 = map.get("key2");
        assertEquals(set2, ImmutableSet.of("D", "E", "F"));
        
        Set<String> set3 = map.get("key3");
        assertEquals(set3, ImmutableSet.of());
    }
    
    @Test(expectedExceptions = PersistenceException.class)
    public void deserializeInvalidValue() {
        CONVERTER.convertToEntityAttribute("{");
    }
}
