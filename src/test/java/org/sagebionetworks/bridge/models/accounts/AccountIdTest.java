package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountIdTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountId.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void twoAccountIdsAreTheSameWithTheSameData() {
        assertTrue(AccountId.forId(TEST_STUDY_IDENTIFIER, "id")
                .equals(AccountId.forId(TEST_STUDY_IDENTIFIER, "id")));
        
        assertTrue(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE)
                .equals(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE)));
        
        assertTrue(AccountId.forEmail(TEST_STUDY_IDENTIFIER, "email")
                .equals(AccountId.forEmail(TEST_STUDY_IDENTIFIER, "email")));
        
        assertTrue(AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "DEF-GHI")
                .equals(AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "DEF-GHI")));
        
        assertTrue(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID")
                .equals(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID")));
        
        assertTrue(AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, SYNAPSE_USER_ID)
                .equals(AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, SYNAPSE_USER_ID)));
    }
    
    @Test
    public void testToString() {
        assertEquals(AccountId.forId(TEST_STUDY_IDENTIFIER, "user-id").toString(), "AccountId [studyId=api, credential=user-id]");
        
        assertEquals(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE).toString(), "AccountId [studyId=api, credential=Phone [regionCode=US, number=9712486796]]");
        
        assertEquals(AccountId.forEmail(TEST_STUDY_IDENTIFIER, "email").toString(), "AccountId [studyId=api, credential=email]");
        
        assertEquals(AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "DEF-GHI").toString(), "AccountId [studyId=api, credential=HEALTH_CODE]");
        
        assertEquals(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID").toString(), "AccountId [studyId=api, credential=EXTID]");
        
        assertEquals(AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, SYNAPSE_USER_ID).toString(), "AccountId [studyId=api, credential="+SYNAPSE_USER_ID+"]");
    }
    
    @Test
    public void factoryMethodsWork() {
        String number = PHONE.getNumber();
        assertEquals(AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getId(), "one");
        assertEquals(AccountId.forEmail(TEST_STUDY_IDENTIFIER, "one").getEmail(), "one");
        assertEquals(AccountId.forPhone(TEST_STUDY_IDENTIFIER, PHONE).getPhone().getNumber(), number);
        assertEquals(AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "ABC-DEF").getHealthCode(), "ABC-DEF");
        assertEquals(AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "EXTID").getExternalId(), "EXTID");
        assertEquals(AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, SYNAPSE_USER_ID).getSynapseUserId(), SYNAPSE_USER_ID);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void idAccessorThrows() {
        AccountId.forEmail(TEST_STUDY_IDENTIFIER, "one").getId();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void emailAccessorThrows() {
        AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getEmail();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void phoneAccessorThrows() {
        AccountId.forId(TEST_STUDY_IDENTIFIER, "one").getPhone();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void healthCodeAccessorThrows() {
        AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, "one").getEmail();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void externalIdAccessorThrows() {
        AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "one").getEmail();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void synapseUserIdAccessorThrows() {
        AccountId.forExternalId(TEST_STUDY_IDENTIFIER, "one").getSynapseUserId();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoEmail() {
        AccountId.forEmail(TEST_STUDY_IDENTIFIER, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoId() {
        AccountId.forId(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoPhone() {
        AccountId.forPhone(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoHealthCode() {
        AccountId.forHealthCode(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoExternalId() {
        AccountId.forExternalId(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoSynapseUserId() {
        AccountId.forSynapseUserId(TEST_STUDY_IDENTIFIER, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudy() {
        AccountId.forId(null, "id");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudyOrEmail() {
        AccountId.forEmail(null, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudyOrId() {
        AccountId.forId(null, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoStudyOrPhone() {
        AccountId.forPhone(null, null);
    }
    
    @Test
    public void getValuesWithoutGuards() {
        AccountId id = AccountId.forId("test-study", "id");
        
        AccountId accountId = id.getUnguardedAccountId();
        assertEquals(accountId.getStudyId(), "test-study");
        assertEquals(accountId.getId(), "id");
        assertNull(accountId.getEmail());
        assertNull(accountId.getPhone());
        assertNull(accountId.getHealthCode());
        assertNull(accountId.getExternalId());
        assertNull(accountId.getSynapseUserId());
    }

    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'study':'api'," +
                "'email': '"+EMAIL+"'," +
                "'healthCode': 'someHealthCode', "+
                "'externalId': 'someExternalId', "+
                "'synapseUserId': 'synapseUserId', "+
                "'phone': {'number': '"+PHONE.getNumber()+"', "+
                "'regionCode':'"+PHONE.getRegionCode()+"'}}");
        
        AccountId identifier = BridgeObjectMapper.get().readValue(json, AccountId.class);
        
        assertEquals(identifier.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(identifier.getEmail(), EMAIL);
        assertEquals(identifier.getExternalId(), "someExternalId");
        assertEquals(identifier.getHealthCode(), "someHealthCode");
        assertEquals(identifier.getSynapseUserId(), "synapseUserId");
        assertEquals(identifier.getPhone().getNumber(), PHONE.getNumber());
        assertEquals(identifier.getPhone().getRegionCode(), PHONE.getRegionCode());
    }
}
