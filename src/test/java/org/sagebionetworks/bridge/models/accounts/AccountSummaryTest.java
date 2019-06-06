package org.sagebionetworks.bridge.models.accounts;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
        DateTime dateTime = DateTime.now().withZone(DateTimeZone.forOffsetHours(-8));
        AccountSummary summary = new AccountSummary("firstName", "lastName", "email@email.com", TestConstants.PHONE,
                "oldExternalId", ImmutableMap.of("sub1", "externalId"), "ABC", dateTime, AccountStatus.UNVERIFIED,
                TestConstants.TEST_STUDY, ImmutableSet.of("sub1", "sub2"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(summary);
        assertEquals(node.get("firstName").textValue(), "firstName");
        assertEquals(node.get("lastName").textValue(), "lastName");
        assertEquals(node.get("email").textValue(), "email@email.com");
        assertEquals(node.get("id").textValue(), "ABC");
        assertEquals(node.get("phone").get("number").textValue(), TestConstants.PHONE.getNumber());
        assertEquals(node.get("phone").get("regionCode").textValue(), TestConstants.PHONE.getRegionCode());
        assertEquals(node.get("phone").get("nationalFormat").textValue(), TestConstants.PHONE.getNationalFormat());
        assertEquals(node.get("externalId").textValue(), "oldExternalId");
        assertEquals(node.get("externalIds").get("sub1").textValue(), "externalId");
        assertEquals(node.get("createdOn").textValue(), dateTime.withZone(DateTimeZone.UTC).toString());
        assertEquals(node.get("status").textValue(), "unverified");
        assertEquals(node.get("studyIdentifier").get("identifier").textValue(), TestConstants.TEST_STUDY_IDENTIFIER);
        assertEquals(node.get("substudyIds").get(0).textValue(), "sub1");
        assertEquals(node.get("substudyIds").get(1).textValue(), "sub2");
        assertEquals(node.get("type").textValue(), "AccountSummary");
        
        AccountSummary newSummary = BridgeObjectMapper.get().treeToValue(node, AccountSummary.class);
        assertEquals(newSummary, summary);
    }
}
