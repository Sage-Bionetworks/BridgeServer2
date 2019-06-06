package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.Sets;

import org.testng.annotations.Test;

public class StringSetMarshallerTest {
    private static final StringSetMarshaller MARSHALLER = new StringSetMarshaller();
    
    @Test
    public void serializationTest() {
        TreeSet<String> orderedSet = new TreeSet<>();
        orderedSet.add("Paris");
        orderedSet.add("Brussels");
        orderedSet.add("London");
        
        String ser = MARSHALLER.convert(orderedSet);
        assertEquals(ser, "[\"Brussels\",\"London\",\"Paris\"]");
        
        Set<String> deser = MARSHALLER.unconvert(ser);
        
        assertEquals(deser, orderedSet);
    }
    
    @Test
    public void serializeEmptySet() {
        String ser = MARSHALLER.convert(Sets.newHashSet());
        assertEquals("[]", ser);
        
        Set<String> deser = MARSHALLER.unconvert(ser);
        assertEquals(deser, Sets.newHashSet());
    }

}
