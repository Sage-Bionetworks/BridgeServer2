package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import org.joda.time.LocalDateTime;
import org.testng.annotations.Test;

public class LocalDateTimeMarshallerTest {
    private static final LocalDateTimeMarshaller MARSHALLER = new LocalDateTimeMarshaller();

    @Test
    public void testMarshall() {
        assertEquals(MARSHALLER.convert(new LocalDateTime(2014, 12, 25, 10, 12, 37, 22)), "2014-12-25T10:12:37.022");
    }

    @Test
    public void testUnmarshall() {
        LocalDateTime dateTime = MARSHALLER.unconvert("2014-10-31T10:12:37.022");
        assertEquals(dateTime.getYear(), 2014);
        assertEquals(dateTime.getMonthOfYear(), 10);
        assertEquals(dateTime.getDayOfMonth(), 31);
        assertEquals(dateTime.getHourOfDay(), 10);
        assertEquals(dateTime.getMinuteOfHour(), 12);
        assertEquals(dateTime.getSecondOfMinute(), 37);
        assertEquals(dateTime.getMillisOfSecond(), 22);
    }
    
    @Test
    public void testUnmarshallOfPartialLocalDateTime() {
        LocalDateTime dateTime = MARSHALLER.unconvert("2014-10-31T10:12");
        assertEquals(2014, dateTime.getYear());
        assertEquals(10, dateTime.getMonthOfYear());
        assertEquals(31, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHourOfDay());
        assertEquals(12, dateTime.getMinuteOfHour());
        assertEquals(0, dateTime.getSecondOfMinute());
        assertEquals(0, dateTime.getMillisOfSecond());
    }

}
