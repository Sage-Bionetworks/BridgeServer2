package org.sagebionetworks.bridge.json;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;

import com.fasterxml.jackson.core.JsonParser;

public class JodaDateTimeDeserializerTest {
    @Test
    public void test() throws Exception {
        // arbitrarily 2014-02-12T16:07 PST
        long expectedMillis = new DateTime(2014, 2, 12, 16, 7, BridgeConstants.LOCAL_TIME_ZONE).getMillis();

        // mock JsonParser
        JsonParser mockJP = mock(JsonParser.class);
        when(mockJP.getText()).thenReturn("2014-02-12T16:07-0800");

        // execute and validate
        DateTime result = new JodaDateTimeDeserializer().deserialize(mockJP, null);
        assertEquals(result.getMillis(), expectedMillis);
        assertEquals(result.toString(), "2014-02-12T16:07:00.000-08:00");
    }
}
