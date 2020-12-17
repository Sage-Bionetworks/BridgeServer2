package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;

public class EnrollmentDetailTest {

    @Test
    public void test() throws Exception {
        Account account1 = Account.create();
        account1.setEmail("email1@email.com");
        AccountRef ref1 = new AccountRef(account1);
        
        Account account2 = Account.create();
        account2.setEmail("email2@email.com");
        AccountRef ref2 = new AccountRef(account2);

        Account account3 = Account.create();
        account3.setEmail("email3@email.com");
        AccountRef ref3 = new AccountRef(account3);
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        enrollment.setExternalId("anExternalId");
        enrollment.setConsentRequired(true);

        EnrollmentDetail detail = new EnrollmentDetail(enrollment, ref1, ref2, ref3);
        
        assertEquals(detail.getExternalId(), "anExternalId");
        assertEquals(detail.getParticipant().getEmail(), "email1@email.com");
        assertEquals(detail.getEnrolledBy().getEmail(), "email2@email.com");
        assertEquals(detail.getWithdrawnBy().getEmail(), "email3@email.com");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(detail);
        assertEquals(node.size(), 7);
        
        // Note that other than email and type properties, there's nothing else exposed about account
        assertEquals(node.get("studyId").textValue(), TEST_STUDY_ID);
        assertEquals(node.get("participant").size(), 2);
        assertEquals(node.get("enrolledBy").size(), 2);
        assertEquals(node.get("withdrawnBy").size(), 2);
        
        assertEquals(node.get("participant").get("email").textValue(), "email1@email.com");
        assertEquals(node.get("enrolledBy").get("email").textValue(), "email2@email.com");
        assertEquals(node.get("withdrawnBy").get("email").textValue(), "email3@email.com");
        assertEquals(node.get("externalId").textValue(), "anExternalId");
        assertTrue(node.get("consentRequired").booleanValue());
        assertEquals(node.get("type").textValue(), "EnrollmentDetail");
        
        // This object is not deserialized on the server.
    }
    
}
