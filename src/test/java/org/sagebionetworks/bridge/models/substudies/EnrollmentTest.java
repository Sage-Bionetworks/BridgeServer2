package org.sagebionetworks.bridge.models.substudies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

public class EnrollmentTest {

    @Test
    public void createSmall() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "substudyId", "accountId");
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getSubstudyId(), "substudyId");
        assertEquals(enrollment.getAccountId(), "accountId");
    }
    
    @Test
    public void createFull() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "substudyId", "accountId", "externalId");
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getSubstudyId(), "substudyId");
        assertEquals(enrollment.getAccountId(), "accountId");
        assertEquals(enrollment.getExternalId(), "externalId");
    }

    @Test
    public void createFullNullExternalId() {
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "substudyId", "accountId", null);
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getSubstudyId(), "substudyId");
        assertEquals(enrollment.getAccountId(), "accountId");
        assertNull(enrollment.getExternalId());
    }
}
