package org.sagebionetworks.bridge.models.accounts;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class GeneratedPasswordTest {
    @Test
    public void test() { 
        GeneratedPassword gp = new GeneratedPassword("externalId", "userId", "password");
        assertEquals(gp.getExternalId(), "externalId");
        assertEquals(gp.getUserId(), "userId");
        assertEquals(gp.getPassword(), "password");
    }
}
