package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_EXTERNAL_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class EnrollmentInfoTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(EnrollmentInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void enrolledAccount() throws Exception { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setExternalId(TEST_EXTERNAL_ID);
        en.setEnrolledOn(CREATED_ON);
        en.setEnrolledBy("other-user");
        en.setConsentRequired(true);
        EnrollmentInfo detail = EnrollmentInfo.create(en);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(detail);
        
        assertEquals(node.get("externalId").textValue(), TEST_EXTERNAL_ID);
        assertTrue(node.get("consentRequired").booleanValue());
        assertEquals(node.get("enrolledOn").textValue(), CREATED_ON.toString());
        assertNull(node.get("enrolledBySelf"));
        assertNull(node.get("withdrawnBySelf"));
        assertEquals(node.get("type").textValue(), "EnrollmentInfo");
        
        EnrollmentInfo deser = BridgeObjectMapper.get().readValue(node.toString(), EnrollmentInfo.class);
        assertEquals(deser, detail);
    }
    
    @Test
    public void enrolledBySelfAccount() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setExternalId(TEST_EXTERNAL_ID);
        en.setEnrolledOn(CREATED_ON);
        en.setConsentRequired(true);
        EnrollmentInfo detail = EnrollmentInfo.create(en);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(detail);
        assertTrue(node.get("enrolledBySelf").booleanValue());
    }
    
    @Test
    public void withdrawnAccount() throws Exception {
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setExternalId(TEST_EXTERNAL_ID);
        en.setEnrolledOn(CREATED_ON);
        en.setEnrolledBy("other-user");
        en.setConsentRequired(true);
        en.setWithdrawnOn(MODIFIED_ON);
        en.setWithdrawnBy("other-user");
        EnrollmentInfo detail = EnrollmentInfo.create(en);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(detail);
        
        assertEquals(node.get("externalId").textValue(), TEST_EXTERNAL_ID);
        assertTrue(node.get("consentRequired").booleanValue());
        assertEquals(node.get("enrolledOn").textValue(), CREATED_ON.toString());
        assertNull(node.get("enrolledBySelf"));
        assertEquals(node.get("withdrawnOn").textValue(), MODIFIED_ON.toString());
        assertNull(node.get("withdrawnBySelf"));
        assertEquals(node.get("type").textValue(), "EnrollmentInfo");
        
        EnrollmentInfo deser = BridgeObjectMapper.get().readValue(node.toString(), EnrollmentInfo.class);
        assertEquals(deser, detail);
    }
    
    @Test
    public void withdrawnBySelfAccount() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setExternalId(TEST_EXTERNAL_ID);
        en.setEnrolledOn(CREATED_ON);
        en.setEnrolledBy("other-user");
        en.setConsentRequired(true);
        en.setWithdrawnOn(MODIFIED_ON);
        EnrollmentInfo detail = EnrollmentInfo.create(en);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(detail);
        assertTrue(node.get("withdrawnBySelf").booleanValue());
    }
}
