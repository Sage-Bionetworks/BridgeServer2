package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
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
        assertTrue(AccountId.forId(API_APP_ID, "id")
                .equals(AccountId.forId(API_APP_ID, "id")));
        
        assertTrue(AccountId.forPhone(API_APP_ID, PHONE)
                .equals(AccountId.forPhone(API_APP_ID, PHONE)));
        
        assertTrue(AccountId.forEmail(API_APP_ID, "email")
                .equals(AccountId.forEmail(API_APP_ID, "email")));
        
        assertTrue(AccountId.forHealthCode(API_APP_ID, "DEF-GHI")
                .equals(AccountId.forHealthCode(API_APP_ID, "DEF-GHI")));
        
        assertTrue(AccountId.forExternalId(API_APP_ID, "EXTID")
                .equals(AccountId.forExternalId(API_APP_ID, "EXTID")));
        
        assertTrue(AccountId.forSynapseUserId(API_APP_ID, SYNAPSE_USER_ID)
                .equals(AccountId.forSynapseUserId(API_APP_ID, SYNAPSE_USER_ID)));
    }
    
    @Test
    public void testToString() {
        assertEquals(AccountId.forId(API_APP_ID, "user-id").toString(), "AccountId [studyId=api, credential=user-id]");
        
        assertEquals(AccountId.forPhone(API_APP_ID, PHONE).toString(), "AccountId [studyId=api, credential=Phone [regionCode=US, number=9712486796]]");
        
        assertEquals(AccountId.forEmail(API_APP_ID, "email").toString(), "AccountId [studyId=api, credential=email]");
        
        assertEquals(AccountId.forHealthCode(API_APP_ID, "DEF-GHI").toString(), "AccountId [studyId=api, credential=HEALTH_CODE]");
        
        assertEquals(AccountId.forExternalId(API_APP_ID, "EXTID").toString(), "AccountId [studyId=api, credential=EXTID]");
        
        assertEquals(AccountId.forSynapseUserId(API_APP_ID, SYNAPSE_USER_ID).toString(), "AccountId [studyId=api, credential="+SYNAPSE_USER_ID+"]");
    }
    
    @Test
    public void factoryMethodsWork() {
        String number = PHONE.getNumber();
        assertEquals(AccountId.forId(API_APP_ID, "one").getId(), "one");
        assertEquals(AccountId.forEmail(API_APP_ID, "one").getEmail(), "one");
        assertEquals(AccountId.forPhone(API_APP_ID, PHONE).getPhone().getNumber(), number);
        assertEquals(AccountId.forHealthCode(API_APP_ID, "ABC-DEF").getHealthCode(), "ABC-DEF");
        assertEquals(AccountId.forExternalId(API_APP_ID, "EXTID").getExternalId(), "EXTID");
        assertEquals(AccountId.forSynapseUserId(API_APP_ID, SYNAPSE_USER_ID).getSynapseUserId(), SYNAPSE_USER_ID);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void idAccessorThrows() {
        AccountId.forEmail(API_APP_ID, "one").getId();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void emailAccessorThrows() {
        AccountId.forId(API_APP_ID, "one").getEmail();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void phoneAccessorThrows() {
        AccountId.forId(API_APP_ID, "one").getPhone();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void healthCodeAccessorThrows() {
        AccountId.forHealthCode(API_APP_ID, "one").getEmail();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void externalIdAccessorThrows() {
        AccountId.forExternalId(API_APP_ID, "one").getEmail();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void synapseUserIdAccessorThrows() {
        AccountId.forExternalId(API_APP_ID, "one").getSynapseUserId();
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoEmail() {
        AccountId.forEmail(API_APP_ID, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoId() {
        AccountId.forId(API_APP_ID, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoPhone() {
        AccountId.forPhone(API_APP_ID, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoHealthCode() {
        AccountId.forHealthCode(API_APP_ID, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoExternalId() {
        AccountId.forExternalId(API_APP_ID, null);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void cannotCreateIdObjectWithNoSynapseUserId() {
        AccountId.forSynapseUserId(API_APP_ID, null);
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
        
        assertEquals(identifier.getStudyId(), API_APP_ID);
        assertEquals(identifier.getEmail(), EMAIL);
        assertEquals(identifier.getExternalId(), "someExternalId");
        assertEquals(identifier.getHealthCode(), "someHealthCode");
        assertEquals(identifier.getSynapseUserId(), "synapseUserId");
        assertEquals(identifier.getPhone().getNumber(), PHONE.getNumber());
        assertEquals(identifier.getPhone().getRegionCode(), PHONE.getRegionCode());
    }
}
