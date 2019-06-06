package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountSummarySearchTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AccountSummarySearch.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void equalityWithChronologyDifference() throws Exception {
        // Why are these not found to be the same in the controller test after serialization/deserialization?
        DateTimeZone losAngeles = DateTimeZone.forID("America/Los_Angeles");
        // This does weird things to Joda Time's Chronology object. Enough to break equality
        DateTimeZone offsetHours = DateTimeZone.forTimeZone(losAngeles.toTimeZone());
        
        DateTime startTime = DateTime.parse("2018-05-22T06:50:21.650-07:00")
                .withChronology(ISOChronology.getInstance(losAngeles));
        DateTime endTime = DateTime.parse("2018-05-22T09:50:21.664-07:00")
                .withChronology(ISOChronology.getInstance(offsetHours));
        
        AccountSummarySearch search1 = new AccountSummarySearch.Builder()
                .withOffsetBy(10)
                .withPageSize(100)
                .withEmailFilter("email")
                .withPhoneFilter("phone")
                .withAllOfGroups(Sets.newHashSet("group1"))
                .withNoneOfGroups(Sets.newHashSet("group2"))
                .withLanguage("en")
                .withStartTime(startTime)
                .withEndTime(endTime)
                .build();
        
        ObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(search1);
        AccountSummarySearch search2 = mapper.readValue(json, AccountSummarySearch.class);
        
        assertEquals(search2, search1);
    }
    
    @Test
    public void canBeSerialized() throws Exception {
        // Verify that our serialization preserves the time zone. This has been an 
        // issue with Jackson.
        DateTime startTime = DateTime.now(DateTimeZone.forOffsetHours(3)).minusDays(2);
        DateTime endTime = DateTime.now(DateTimeZone.forOffsetHours(3));
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
            .withOffsetBy(10)
            .withPageSize(100)
            .withEmailFilter("email")
            .withPhoneFilter("phone")
            .withAllOfGroups(Sets.newHashSet("group1"))
            .withNoneOfGroups(Sets.newHashSet("group2"))
            .withLanguage("en")
            .withStartTime(startTime)
            .withEndTime(endTime).build();
        
        String json = BridgeObjectMapper.get().writeValueAsString(search);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        // The one JSON property with no analogue in the object itself, verify it's correct
        assertEquals(node.get("type").textValue(), "AccountSummarySearch");
        
        AccountSummarySearch deser = BridgeObjectMapper.get().readValue(json, AccountSummarySearch.class);
        
        assertEquals(deser.getOffsetBy(), 10);
        assertEquals(deser.getPageSize(), 100);
        assertEquals(deser.getEmailFilter(), "email");
        assertEquals(deser.getPhoneFilter(), "phone");
        assertEquals(deser.getAllOfGroups(), Sets.newHashSet("group1"));
        assertEquals(deser.getNoneOfGroups(), Sets.newHashSet("group2"));
        assertEquals(deser.getLanguage(), "en");
        assertEquals(deser.getStartTime(), startTime);
        assertEquals(deser.getEndTime(), endTime);
    }
    
    @Test
    public void setsDefaults() {
        assertEquals(AccountSummarySearch.EMPTY_SEARCH.getOffsetBy(), 0);
        assertEquals(AccountSummarySearch.EMPTY_SEARCH.getPageSize(), BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }
}
