package org.sagebionetworks.bridge.models.subpopulations;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SubpopulationGuidTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SubpopulationGuid.class).verify();
    }
    @Test
    public void testToString() {
        assertEquals(SubpopulationGuid.create("ABC").toString(), "ABC");
    }
}
