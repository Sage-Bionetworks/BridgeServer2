package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SUMMARY1;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountSummaryTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountSummary.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        // Set the time zone so it's not UTC, it should be converted to UTC so the strings are 
        // equal below (to demonstrate the ISO 8601 string is in UTC time zone).
        JsonNode node = BridgeObjectMapper.get().valueToTree(SUMMARY1);
        assertEquals(node.get("firstName").textValue(), "firstName1");
        assertEquals(node.get("lastName").textValue(), "lastName1");
        assertEquals(node.get("email").textValue(), EMAIL);
        assertEquals(node.get("synapseUserId").textValue(), SYNAPSE_USER_ID);
        assertEquals(node.get("id").textValue(), "id");
        assertEquals(node.get("phone").get("number").textValue(), PHONE.getNumber());
        assertEquals(node.get("phone").get("regionCode").textValue(), PHONE.getRegionCode());
        assertEquals(node.get("phone").get("nationalFormat").textValue(), PHONE.getNationalFormat());
        assertEquals(node.get("externalIds").get("substudy1").textValue(), "externalId1");
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("status").textValue(), "disabled");
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("substudyIds").get(0).textValue(), "substudy1");
        assertEquals(node.get("substudyIds").get(1).textValue(), "substudy2");
        assertEquals(node.get("externalId").textValue(), "externalId1");
        assertEquals(node.get("orgMembership").textValue(), TEST_ORG_ID);
        assertEquals(node.get("type").textValue(), "AccountSummary");
        
        AccountSummary newSummary = BridgeObjectMapper.get().treeToValue(node, AccountSummary.class);
        assertEquals(newSummary, SUMMARY1);
    }
    
    @Test
    public void serializationDoesntBreakOnNullExternalIdMap() {
        AccountSummary summary = new AccountSummary.Builder().build();
        JsonNode node = BridgeObjectMapper.get().valueToTree(summary);
        assertNull(node.get("externalId"));
    }
}
