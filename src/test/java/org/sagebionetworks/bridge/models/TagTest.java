package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * Tag is not directly serialized in the API, so this test is very simple.
 */
public class TagTest {

    @Test
    public void test() { 
        Tag tag = new Tag();
        tag.setCategory("cat");
        tag.setValue("value");
        assertEquals(tag.getCategory(), "cat");
        assertEquals(tag.getValue(), "value");
        
        tag = new Tag("value", "cat");
        assertEquals(tag.getCategory(), "cat");
        assertEquals(tag.getValue(), "value");
    }
}
