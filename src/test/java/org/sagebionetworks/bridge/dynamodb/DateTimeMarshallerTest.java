package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DateTimeMarshallerTest {
    private static final DateTimeMarshaller MARSHALLER = new DateTimeMarshaller();

    @Test
    public void testMarshall() {
        assertEquals(MARSHALLER.convert(new DateTime(2014, 12, 25, 10, 12, 37, 22)), "2014-12-25T10:12:37.022-08:00");
    }

    @Test
    public void testUnmarshall() {
        DateTime dateTime = MARSHALLER.unconvert("2014-10-31T10:12:37.022-08:00");
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
        DateTime dateTime = MARSHALLER.unconvert("2014-10-31T10:12-08:00");
        assertEquals(2014, dateTime.getYear());
        assertEquals(10, dateTime.getMonthOfYear());
        assertEquals(31, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHourOfDay());
        assertEquals(12, dateTime.getMinuteOfHour());
        assertEquals(0, dateTime.getSecondOfMinute());
        assertEquals(0, dateTime.getMillisOfSecond());
    }

}
