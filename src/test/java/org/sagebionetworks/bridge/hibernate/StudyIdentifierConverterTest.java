package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class StudyIdentifierConverterTest {

    static final StudyIdentifierConverter CONVERTER = new StudyIdentifierConverter();
    
    @Test
    public void convertToDatabaseColumn() throws Exception {
        String string = CONVERTER.convertToDatabaseColumn(TEST_STUDY);
        
        assertEquals(string, API_APP_ID);
    }

    @Test
    public void convertToDatabaseColumnNull() throws Exception {
        String value = CONVERTER.convertToDatabaseColumn(null);
        assertNull(value);
    }
    
    @Test
    public void convertToEntityAttribute() {
        String value = CONVERTER.convertToDatabaseColumn(TEST_STUDY);
        
        StudyIdentifier deser = CONVERTER.convertToEntityAttribute(value);
        assertEquals(deser, TEST_STUDY);
    }

    @Test
    public void convertToEntityAttributeNull() {
        StudyIdentifier deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }
}
