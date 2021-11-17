package org.sagebionetworks.bridge;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.regex.Pattern;

import org.testng.annotations.Test;

public class PasswordGeneratorTest {

    @Test
    public void generatesPasswordCorrectLength() {
        assertEquals(PasswordGenerator.INSTANCE.nextPassword(16).length(), 16);
        assertEquals(PasswordGenerator.INSTANCE.nextPassword(101).length(), 101);
    }
    
    @Test
    public void containsEveryClassOfCharacter() {
        String password = PasswordGenerator.INSTANCE.nextPassword(32);
        assertTrue(password.matches(".*["+Pattern.quote("!#$%&'()*+,-./:;<=>?@[]^_`{|}~")+"].*"));
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*[0-9].*"));
    }
}
