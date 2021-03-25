package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.LocalTime;
import org.testng.annotations.Test;

public class LocalTimeToStringConverterTest {

    // Note that time window has a formatter on it to truncate the LocalDate value
    // to HH:mm only. So we're testing that here.
    private static final LocalTime TIME = LocalTime.parse("10:00");
    
    @Test
    public void convertToDatabaseColumn() {
        LocalTimeToStringConverter converter = new LocalTimeToStringConverter();
        
        assertNull(converter.convertToDatabaseColumn(null));
        assertEquals(converter.convertToDatabaseColumn(TIME), "10:00:00.000");
    }

    @Test
    public void convertToEntityAttribute() {
        LocalTimeToStringConverter converter = new LocalTimeToStringConverter();
        
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals(converter.convertToEntityAttribute("10:00"), TIME);
    }
}
