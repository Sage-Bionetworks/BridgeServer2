package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoSubpopulationTest {
    
    private static final Set<String> ALL_OF_GROUPS = Sets.newHashSet("requiredGroup");
    private static final Set<String> NONE_OF_GROUPS = Sets.newHashSet("prohibitedGroup");
    private static final DateTime PUBLISHED_CONSENT_TIMESTAMP = DateTime.parse("2016-07-12T19:49:07.415Z");

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoSubpopulation.class).suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void accessStudyId() {
        Subpopulation subpop = makeSubpopulation();
        // This accessor gets one of the values
        assertTrue(subpop.getStudyIdsAssignedOnConsent().contains(subpop.getStudyId()));
    }
    
    @Test
    public void canSerialize() throws Exception {
        Subpopulation subpop = makeSubpopulation();
        
        Criteria criteria = TestUtils.createCriteria(2, 10, ALL_OF_GROUPS, NONE_OF_GROUPS);
        subpop.setCriteria(criteria);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(subpop);

        // This does not need to be passed to the user; the user is never allowed to set it.
        // This should be standard across the API, BTW, but this is leaked out by some classes.
        assertEquals(node.get("name").textValue(), "Name");
        assertEquals(node.get("description").textValue(), "Description");
        assertEquals(node.get("guid").textValue(), "guid");
        assertEquals(node.get("publishedConsentCreatedOn").textValue(), PUBLISHED_CONSENT_TIMESTAMP.toString());
        assertTrue(node.get("required").booleanValue());
        assertTrue(node.get("defaultGroup").booleanValue());
        assertTrue(node.get("autoSendConsentSuppressed").booleanValue());
        assertTrue(node.get("deleted").booleanValue()); // users do not see this flag, they never get deleted items
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("version").longValue(), 3L);
        
        Set<String> dataGroups = ImmutableSet.of(node.get("dataGroupsAssignedWhileConsented").get(0).textValue(),
                node.get("dataGroupsAssignedWhileConsented").get(1).textValue());                
        assertEquals(dataGroups, TestConstants.USER_DATA_GROUPS);
        
        Set<String> studyIds = ImmutableSet.of(node.get("studyIdsAssignedOnConsent").get(0).textValue(),
                node.get("studyIdsAssignedOnConsent").get(1).textValue());
        assertEquals(studyIds, TestConstants.USER_STUDY_IDS);
        assertEquals(node.get("type").textValue(), "Subpopulation");
        
        JsonNode critNode = node.get("criteria");
        assertEquals(JsonUtils.asStringSet(critNode, "allOfGroups"), ALL_OF_GROUPS);
        assertEquals(JsonUtils.asStringSet(critNode, "noneOfGroups"), NONE_OF_GROUPS);
        assertEquals(critNode.get("minAppVersions").get(IOS).asInt(), 2);
        assertEquals(critNode.get("maxAppVersions").get(IOS).asInt(), 10);
        
        Subpopulation newSubpop = BridgeObjectMapper.get().treeToValue(node, Subpopulation.class);
        // Not serialized, these values have to be added back to have equal objects 
        newSubpop.setAppId(TEST_APP_ID);
        
        assertEquals(newSubpop, subpop);
        
        // Finally, check the publication site URLs
        assertEqualsAndNotNull(newSubpop.getConsentHTML(), JsonUtils.asText(node, "consentHTML"));
        assertEqualsAndNotNull(newSubpop.getConsentPDF(), JsonUtils.asText(node, "consentPDF"));

        String htmlURL = "http://" + BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs") + "/" + newSubpop.getGuidString() + "/consent.html";
        assertEquals(newSubpop.getConsentHTML(), htmlURL);
        
        String pdfURL = "http://" + BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs") + "/" + newSubpop.getGuidString() + "/consent.pdf";
        assertEquals(newSubpop.getConsentPDF(), pdfURL);
        
        Criteria critObject = newSubpop.getCriteria();
        assertEquals(critObject.getMinAppVersion(IOS), new Integer(2));
        assertEquals(critObject.getMaxAppVersion(IOS), new Integer(10));
        assertEquals(critObject.getAllOfGroups(), ALL_OF_GROUPS);
        assertEquals(critObject.getNoneOfGroups(), NONE_OF_GROUPS);
    }

    private Subpopulation makeSubpopulation() {
        Subpopulation subpop = new DynamoSubpopulation();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setGuidString("guid");
        subpop.setAppId(TEST_APP_ID);
        subpop.setRequired(true);
        subpop.setDefaultGroup(true);
        subpop.setPublishedConsentCreatedOn(PUBLISHED_CONSENT_TIMESTAMP.getMillis());
        subpop.setDeleted(true);
        subpop.setAutoSendConsentSuppressed(true);
        subpop.setVersion(3L);
        subpop.setDataGroupsAssignedWhileConsented(TestConstants.USER_DATA_GROUPS);
        subpop.setStudyIdsAssignedOnConsent(TestConstants.USER_STUDY_IDS);
        return subpop;
    }
    
    @Test
    public void cannotNullifySets() {
        Subpopulation subpop = new DynamoSubpopulation();
        // Set some values to verify that null resets these to the empty set
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of("A"));
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of("B"));
        assertEquals(subpop.getDataGroupsAssignedWhileConsented(), ImmutableSet.of("A"));
        assertEquals(subpop.getStudyIdsAssignedOnConsent(), ImmutableSet.of("B"));
        
        subpop.setDataGroupsAssignedWhileConsented(null);
        subpop.setStudyIdsAssignedOnConsent(null);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
        assertTrue(subpop.getStudyIdsAssignedOnConsent().isEmpty());
    }
    
    @Test
    public void publicInterfaceWriterWorks() throws Exception {
        Subpopulation subpop = makeSubpopulation();

        String json = Subpopulation.SUBPOP_WRITER.writeValueAsString(subpop);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertNull(node.get("appId"));
    }
    
    @Test
    public void guidAndGuidStringInterchangeable() throws Exception {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SubpopulationGuid.create("abc"));
        assertEquals(subpop.getGuid().getGuid(), "abc");
        
        String json = BridgeObjectMapper.get().writeValueAsString(subpop);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("guid").asText(), "abc");
        assertNull(node.get("guidString"));
        
        Subpopulation newSubpop = BridgeObjectMapper.get().readValue(json, Subpopulation.class);
        assertEquals(newSubpop.getGuidString(), "abc");
        assertEquals(newSubpop.getGuid().getGuid(), "abc");

        subpop = Subpopulation.create();
        subpop.setGuidString("abc");
        assertEquals(subpop.getGuidString(), "abc");
        
        json = BridgeObjectMapper.get().writeValueAsString(subpop);
        node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("guid").asText(), "abc");
        assertNull(node.get("guidString"));
        
        newSubpop = BridgeObjectMapper.get().readValue(json, Subpopulation.class);
        assertEquals(newSubpop.getGuidString(), "abc");
        assertEquals(newSubpop.getGuid().getGuid(), "abc");
    }
    
    void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(actual, expected);
    }
    
}
