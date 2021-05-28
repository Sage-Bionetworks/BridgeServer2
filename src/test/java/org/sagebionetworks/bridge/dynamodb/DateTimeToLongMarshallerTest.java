package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

public class DateTimeToLongMarshallerTest {
    
    DateTimeToLongMarshaller marshaller = new DateTimeToLongMarshaller();
    
    @Test
    public void convert() {
        Long result = marshaller.convert(CREATED_ON);
        assertEquals(result.longValue(), CREATED_ON.getMillis());
    }

    @Test
    public void unconvert() {
        DateTime result = marshaller.unconvert(Long.valueOf(CREATED_ON.getMillis()));
        assertTrue(result.isEqual(CREATED_ON));
    }

    @Test
    public void convertHandlesNull() {
        assertNull( marshaller.convert(null) );
    }

    @Test
    public void unconvertHandlesNull() {
        assertNull( marshaller.unconvert(null) );
    }
}
