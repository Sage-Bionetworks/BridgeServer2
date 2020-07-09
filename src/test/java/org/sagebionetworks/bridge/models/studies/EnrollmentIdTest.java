package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.EnrollmentId;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class EnrollmentIdTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(EnrollmentId.class).allFieldsShouldBeUsed().suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
    
    @Test
    public void create() { 
        EnrollmentId key = new EnrollmentId(TEST_APP_ID, "studyId", "accountId");
        assertEquals(key.getAppId(), TEST_APP_ID);
        assertEquals(key.getStudyId(), "studyId");
        assertEquals(key.getAccountId(), "accountId");
    }
    
}
