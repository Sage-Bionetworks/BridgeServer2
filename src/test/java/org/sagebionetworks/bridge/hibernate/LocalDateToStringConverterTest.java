package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LocalDateToStringConverterTest extends Mockito {

    private static final String STRING = "2016-04-13";
    private static final LocalDate DATETIME = LocalDate.parse(STRING);

    LocalDateToStringConverter converter;
    
    @BeforeMethod
    public void beforeMethod() {
        converter = new LocalDateToStringConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(converter.convertToDatabaseColumn(DATETIME), STRING);
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(converter.convertToEntityAttribute(STRING), DATETIME);
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
