package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.studies.EnrollmentFilter.ENROLLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

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
                .withOrgMembership(TEST_ORG_ID)
                .withEnrolledInStudyId(TEST_STUDY_ID)
                .withExternalIdFilter("externalId")
                .withStatus(ENABLED)
                .withEnrollment(ENROLLED)
                .withAttributeKey("foo")
                .withAttributeValueFilter("bar")
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
            .withEndTime(endTime)
            // These three flags are not all set at the same time when in use
            // but they can all be tested for serialization at one time.
            .withOrgMembership(TEST_ORG_ID)
            .withAdminOnly(true)
            .withEnrolledInStudyId(TEST_STUDY_ID)
            .withExternalIdFilter("externalId")
            .withStatus(ENABLED)
            .withEnrollment(ENROLLED)
            .withAttributeKey("foo")
            .withAttributeValueFilter("bar")
            .build();
        
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
        assertEquals(deser.getOrgMembership(), TEST_ORG_ID);
        assertTrue(deser.isAdminOnly());
        assertEquals(deser.getEnrolledInStudyId(), TEST_STUDY_ID);
        assertEquals(deser.getExternalIdFilter(), "externalId");
        assertEquals(deser.getStatus(), ENABLED);
        assertEquals(deser.getEnrollment(), ENROLLED);
        assertEquals(deser.getAttributeKey(), "foo");
        assertEquals(deser.getAttributeValueFilter(), "bar");
    }
    
    @Test
    public void copyOfWorks() { 
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
            .withEndTime(endTime)
            .withAdminOnly(false)
            .withOrgMembership(TEST_ORG_ID)
            .withEnrolledInStudyId(TEST_STUDY_ID)
            .withExternalIdFilter("externalId")
            .withStatus(ENABLED)
            .withEnrollment(ENROLLED)
            .withAttributeKey("foo")
            .withAttributeValueFilter("bar")
            .build();

        AccountSummarySearch copy = new AccountSummarySearch.Builder().copyOf(search).build();
        assertEquals(copy.getOffsetBy(), 10);
        assertEquals(copy.getPageSize(), 100);
        assertEquals(copy.getEmailFilter(), "email");
        assertEquals(copy.getPhoneFilter(), "phone");
        assertEquals(copy.getAllOfGroups(), Sets.newHashSet("group1"));
        assertEquals(copy.getNoneOfGroups(), Sets.newHashSet("group2"));
        assertEquals(copy.getLanguage(), "en");
        assertEquals(copy.getStartTime(), startTime);
        assertEquals(copy.getEndTime(), endTime);
        assertEquals(copy.getOrgMembership(), TEST_ORG_ID);
        assertEquals(copy.isAdminOnly(), Boolean.FALSE);
        assertEquals(copy.getEnrolledInStudyId(), TEST_STUDY_ID);
        assertEquals(copy.getExternalIdFilter(), "externalId");
        assertEquals(copy.getStatus(), ENABLED);
        assertEquals(copy.getEnrollment(), ENROLLED);
        assertEquals(copy.getAttributeKey(), "foo");
        assertEquals(copy.getAttributeValueFilter(), "bar");
    }
    
    @Test
    public void setsDefaults() {
        assertEquals(AccountSummarySearch.EMPTY_SEARCH.getOffsetBy(), 0);
        assertEquals(AccountSummarySearch.EMPTY_SEARCH.getPageSize(), BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }
}
