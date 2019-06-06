package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.DateTimeZone;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DateTimeZoneAttributeConverterTest {
    
    private static final DateTimeZone ZONE = DateTimeZone.forOffsetHours(-7);
    private static final String OFFSET_STRING = "-07:00";

    private DateTimeZoneAttributeConverter converter;
    
    @BeforeMethod
    public void before() {
        converter = new DateTimeZoneAttributeConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(converter.convertToDatabaseColumn(ZONE), OFFSET_STRING);
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(converter.convertToEntityAttribute(OFFSET_STRING), ZONE);
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
