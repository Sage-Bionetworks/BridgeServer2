package org.sagebionetworks.bridge.models.substudies;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class SubstudyIdTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SubstudyId.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        SubstudyId studyId = new SubstudyId("studyId", "id");
        
        assertEquals(studyId.getStudyId(), "studyId");
        assertEquals(studyId.getId(), "id");
    }    
}
