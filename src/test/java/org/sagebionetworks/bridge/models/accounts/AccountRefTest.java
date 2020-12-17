package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.BCRYPT;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class AccountRefTest {
    
    @Test
    public void test() throws Exception {
        Account account = Account.create();
        account.setFirstName("firstName");
        account.setLastName("lastName");
        account.setAttributes(ImmutableMap.of("a", "b", "c", "d"));
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setSynapseUserId("synapseUserId");
        account.setEmailVerified(true);
        account.setPhoneVerified(true);
        account.setReauthToken("reauthToken");
        account.setHealthCode("healthCode");
        account.setStatus(DISABLED);
        account.setAppId(TEST_APP_ID);
        account.setOrgMembership(TEST_ORG_ID);
        account.setRoles(ImmutableSet.of(DEVELOPER));
        account.setId(TEST_USER_ID);
        account.setCreatedOn(CREATED_ON);
        account.setModifiedOn(MODIFIED_ON);
        account.setClientData(TestUtils.getClientData());
        account.setTimeZone(DateTimeZone.UTC);
        account.setSharingScope(ALL_QUALIFIED_RESEARCHERS);
        account.setNotifyByEmail(true);
        account.setDataGroups(USER_DATA_GROUPS);
        account.setLanguages(LANGUAGES);
        account.setMigrationVersion(3);
        account.setVersion(3);
        account.setPasswordHash("passwordHash");
        account.setPasswordModifiedOn(MODIFIED_ON);
        account.setPasswordAlgorithm(BCRYPT);
        account.setEnrollments(ImmutableSet.of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)));
        
        AccountRef ref = new AccountRef(account);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(ref);
        assertEquals(node.size(), 8);
        assertEquals(node.get("firstName").textValue(), "firstName");
        assertEquals(node.get("lastName").textValue(), "lastName");
        assertEquals(node.get("email").textValue(), EMAIL);
        assertEquals(node.get("phone").get("number").textValue(), PHONE.getNumber());
        assertEquals(node.get("synapseUserId").textValue(), "synapseUserId");
        assertEquals(node.get("orgMembership").textValue(), TEST_ORG_ID);
        assertEquals(node.get("identifier").textValue(), TEST_USER_ID);
        assertEquals(node.get("type").textValue(), "AccountRef");
        
        // This reference object is never deserialized on the server.
    }

}
