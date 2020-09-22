package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class EnrollmentMigrationTest {
    
    @Test
    public void test() throws Exception {
        EnrollmentMigration enrollment = new EnrollmentMigration(); 
        enrollment.setAppId(TEST_APP_ID);
        enrollment.setStudyId(TEST_STUDY_ID);
        enrollment.setUserId(USER_ID);
        enrollment.setExternalId("externalId");
        enrollment.setEnrolledOn(CREATED_ON);
        enrollment.setWithdrawnOn(MODIFIED_ON);
        enrollment.setEnrolledBy("enrolledBy");
        enrollment.setWithdrawnBy("withdrawnBy");
        enrollment.setWithdrawalNote("withdrawalNote");
        enrollment.setConsentRequired(true);
        
        String json = BridgeObjectMapper.get().writeValueAsString(enrollment);
        EnrollmentMigration em = BridgeObjectMapper.get().readValue(json, EnrollmentMigration.class);
        
        assertEquals(em.getAppId(), TEST_APP_ID);
        assertEquals(em.getStudyId(), TEST_STUDY_ID);
        assertEquals(em.getUserId(), USER_ID);
        assertEquals(em.getExternalId(), "externalId");
        assertEquals(em.getEnrolledOn(), CREATED_ON);
        assertEquals(em.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(em.getEnrolledBy(), "enrolledBy");
        assertEquals(em.getWithdrawnBy(), "withdrawnBy");
        assertEquals(em.getWithdrawalNote(), "withdrawalNote");
        assertTrue(em.isConsentRequired());
        
        Enrollment en = em.asEnrollment();
        assertEquals(en.getAppId(), TEST_APP_ID);
        assertEquals(en.getStudyId(), TEST_STUDY_ID);
        assertEquals(en.getAccountId(), USER_ID);
        assertEquals(en.getExternalId(), "externalId");
        assertEquals(en.getEnrolledOn(), CREATED_ON);
        assertEquals(en.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(en.getEnrolledBy(), "enrolledBy");
        assertEquals(en.getWithdrawnBy(), "withdrawnBy");
        assertEquals(en.getWithdrawalNote(), "withdrawalNote");
        assertTrue(en.isConsentRequired());
    }

}
