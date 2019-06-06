package org.sagebionetworks.bridge;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

public class SecureTokenGeneratorTest {
    
    @Test
    public void testNoArgConstructor() {
        String value = SecureTokenGenerator.INSTANCE.nextToken();
        
        assertEquals(value.length(), 21);
        assertNotEquals(value, SecureTokenGenerator.INSTANCE.nextToken());
    }
    
    @Test
    public void nextStringDifferent() {
        assertNotEquals(SecureTokenGenerator.INSTANCE.nextToken(), SecureTokenGenerator.INSTANCE.nextToken());
    }
    
    @Test
    public void phoneCodeString() {
        String token = SecureTokenGenerator.PHONE_CODE_INSTANCE.nextToken();
        assertEquals(token.length(), 6);
        assertTrue(token.matches("^\\d+$")); // composed only of digits
    }
    
}
