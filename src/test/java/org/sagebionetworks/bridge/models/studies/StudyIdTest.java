package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class StudyIdTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(StudyId.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        StudyId studyId = new StudyId(TEST_APP_ID, "id");
        
        assertEquals(studyId.getAppId(), TEST_APP_ID);
        assertEquals(studyId.getIdentifier(), "id");
    }    
}
