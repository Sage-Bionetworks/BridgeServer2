package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DateTimeToLongAttributeConverterTest {

    private static final Long MILLIS = new Long(1460542200000L);
    private static final DateTime DATETIME = DateTime.parse("2016-04-13T10:10:00.000Z");
    
    private DateTimeToLongAttributeConverter converter;
    
    @BeforeMethod
    public void before() {
        converter = new DateTimeToLongAttributeConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(converter.convertToDatabaseColumn(DATETIME), MILLIS);
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(converter.convertToEntityAttribute(MILLIS), DATETIME);
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
