package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

public class RangeTupleTest {

    @Test
    public void test() {
        RangeTuple<String> tuple = new RangeTuple<>("startValue", "endValue");
        
        assertEquals(tuple.getStart(), "startValue");
        assertEquals(tuple.getEnd(), "endValue");
    }
    
    @Test
    public void testNull() {
        RangeTuple<String> tuple = new RangeTuple<>(null, null);
        
        assertNull(tuple.getStart());
        assertNull(tuple.getEnd());
    }
}
