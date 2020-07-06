package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateEnrollmentTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(HibernateEnrollment.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        HibernateEnrollment enrollment = new HibernateEnrollment(TEST_APP_ID, "studyId", "accountId", "externalId");
        
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getStudyId(), "studyId");
        assertEquals(enrollment.getAccountId(), "accountId");
        assertEquals(enrollment.getExternalId(), "externalId");
        
        enrollment.setStudyId("newStudyId");
        assertEquals(enrollment.getStudyId(), "newStudyId");
    }
}
