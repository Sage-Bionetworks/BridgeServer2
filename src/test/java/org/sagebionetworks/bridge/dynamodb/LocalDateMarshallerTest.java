package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

public class LocalDateMarshallerTest {
    private static final LocalDateMarshaller MARSHALLER = new LocalDateMarshaller();

    @Test
    public void testMarshall() {
        assertEquals(MARSHALLER.convert(new LocalDate(2014, 12, 25)), "2014-12-25");
    }

    @Test
    public void testUnmarshall() {
        LocalDate calendarDate = MARSHALLER.unconvert("2014-10-31");
        assertEquals(calendarDate.getYear(), 2014);
        assertEquals(calendarDate.getMonthOfYear(), 10);
        assertEquals(calendarDate.getDayOfMonth(), 31);
    }
}
