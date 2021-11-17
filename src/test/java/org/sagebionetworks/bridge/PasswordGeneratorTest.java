package org.sagebionetworks.bridge;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
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

    @Test
    public void randomSymbolDistribution() {
        // Test 10000 bridge passwords for validity
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            counts.put(i, 0);
        }

        String symbols = "!#$%&'()*+,-./:;<=>?@[]^_`{|}~";
        for (int i = 0; i < 10000; i++) {
            String password = PasswordGenerator.INSTANCE.nextPassword(9);
            // Add to the counts of where each symbol is
            for (int j = 0; j < symbols.length(); j++) {
                int symbolIdx =  password.indexOf(symbols.charAt(j));
                if (symbolIdx >= 0) {
                    counts.put(symbolIdx, counts.get(symbolIdx) + 1);
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            // Make sure each index has at least 1% of the distribution
            assertTrue(counts.get(i) > 10);
        }
    }
}
