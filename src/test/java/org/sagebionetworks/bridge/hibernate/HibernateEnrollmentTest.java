package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Enrollment;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateEnrollmentTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(HibernateEnrollment.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Enrollment enrollment = new HibernateEnrollment();
        enrollment.setAppId(TEST_APP_ID);
        enrollment.setStudyId(TEST_STUDY_ID);
        enrollment.setAccountId("accountId");
        enrollment.setExternalId("externalId");
        enrollment.setEnrolledOn(CREATED_ON);
        enrollment.setWithdrawnOn(MODIFIED_ON);
        enrollment.setEnrolledBy("enrolledBy");
        enrollment.setWithdrawnBy("withdrawnBy");
        enrollment.setWithdrawalNote("note");
        enrollment.setConsentRequired(true);
        enrollment.setNote(TEST_NOTE);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(enrollment);
        
        assertEquals(node.get("userId").textValue(), "accountId");
        assertEquals(node.get("externalId").textValue(), "externalId");
        assertEquals(node.get("enrolledOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("enrolledBy").textValue(), "enrolledBy");
        assertEquals(node.get("withdrawnOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("withdrawnBy").textValue(), "withdrawnBy");
        assertEquals(node.get("withdrawalNote").textValue(), "note");
        assertTrue(node.get("consentRequired").booleanValue());
        assertEquals(node.get("note").textValue(), TEST_NOTE);
        assertEquals(node.get("type").textValue(), "Enrollment");
        
        Enrollment deser = BridgeObjectMapper.get().readValue(node.toString(), Enrollment.class);
        assertNull(deser.getAppId());
        assertNull(deser.getStudyId());
        assertEquals(deser.getAccountId(), "accountId");
        assertEquals(deser.getExternalId(), "externalId");
        assertEquals(deser.getEnrolledOn(), CREATED_ON);
        assertEquals(deser.getEnrolledBy(), "enrolledBy");
        assertEquals(deser.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(deser.getWithdrawnBy(), "withdrawnBy");
        assertEquals(deser.getWithdrawalNote(), "note");
        assertTrue(deser.isConsentRequired());
        assertEquals(deser.getNote(), TEST_NOTE);
    }
    
    @Test
    public void test() {
        Enrollment enrollment = new HibernateEnrollment();
        enrollment.setAppId(TEST_APP_ID);
        enrollment.setStudyId(TEST_STUDY_ID);
        enrollment.setAccountId("accountId");
        enrollment.setExternalId("externalId");
        enrollment.setEnrolledOn(CREATED_ON);
        enrollment.setWithdrawnOn(MODIFIED_ON);
        enrollment.setEnrolledBy("enrolledBy");
        enrollment.setWithdrawnBy("withdrawnBy");
        enrollment.setWithdrawalNote("note");
        enrollment.setConsentRequired(true);
        enrollment.setNote(TEST_NOTE);
        
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getStudyId(), TEST_STUDY_ID);
        assertEquals(enrollment.getAccountId(), "accountId");
        assertEquals(enrollment.getExternalId(), "externalId");
        assertEquals(enrollment.getEnrolledOn(), CREATED_ON);
        assertEquals(enrollment.getWithdrawnOn(), MODIFIED_ON);
        assertEquals(enrollment.getEnrolledBy(), "enrolledBy");
        assertEquals(enrollment.getWithdrawnBy(), "withdrawnBy");
        assertEquals(enrollment.getWithdrawalNote(), "note");
        assertEquals(enrollment.getNote(), TEST_NOTE);
    }
}
