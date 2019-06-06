package org.sagebionetworks.bridge.models.sms;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class SmsTypeTest {
    @Test
    public void testGetValue() {
        assertEquals(SmsType.PROMOTIONAL.getValue(), "Promotional");
        assertEquals(SmsType.TRANSACTIONAL.getValue(), "Transactional");
    }
}
