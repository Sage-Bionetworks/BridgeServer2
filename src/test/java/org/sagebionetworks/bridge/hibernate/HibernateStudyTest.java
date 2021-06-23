package org.sagebionetworks.bridge.hibernate;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;

import static org.sagebionetworks.bridge.TestConstants.COLOR_SCHEME;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.studies.IrbDecisionType.APPROVED;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.ANALYSIS;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

public class HibernateStudyTest {

    private static final String TEST_LINK = "http://some.link/";
    private static final DateTime CREATED_ON = DateTime.now().withZone(DateTimeZone.UTC);
    private static final DateTime MODIFIED_ON = DateTime.now().minusHours(1).withZone(DateTimeZone.UTC);
    private static final LocalDate APPROVED_ON = DateTime.now().toLocalDate();
    private static final LocalDate EXPIRES_ON = DateTime.now().plusDays(10).toLocalDate();
    
    @Test
    public void shortConstructor() {
        HibernateStudy study = new HibernateStudy("name", "identifier", "appId", 
                CREATED_ON, MODIFIED_ON, true, DESIGN, TEST_LINK, 10L);
        assertEquals(study.getName(), "name");
        assertEquals(study.getIdentifier(), "identifier");
        assertEquals(study.getAppId(), "appId");
        assertEquals(study.getCreatedOn(), CREATED_ON);
        assertEquals(study.getModifiedOn(), MODIFIED_ON);
        assertTrue(study.isDeleted());
        assertEquals(study.getPhase(), DESIGN);
        assertEquals(study.getLogoURL(), TEST_LINK);
        assertEquals(study.getVersion(), Long.valueOf(10L));
    }
    
    @Test
    public void collectionsNotNull() {
        HibernateStudy study = new HibernateStudy();
        assertNotNull(study.getContacts());
    }
    
    @Test
    public void canSerialize() throws Exception {
        Study study = Study.create();
        study.setIdentifier("oneId");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        study.setDeleted(true);
        study.setCreatedOn(CREATED_ON);
        study.setModifiedOn(MODIFIED_ON);
        study.setClientData(TestUtils.getClientData());
        study.setLogoGuid(GUID);
        study.setLogoURL(TEST_LINK);
        study.setVersion(3L);
        
        Contact c1 = new Contact();
        c1.setName("Name1");
        Contact c2 = new Contact();
        c2.setName("Name2");
        study.setContacts(ImmutableList.of(c1, c2));
        study.setDetails("someDetails");
        study.setIrbName("WIRB");
        study.setIrbDecisionOn(APPROVED_ON);
        study.setIrbExpiresOn(EXPIRES_ON);
        study.setIrbDecisionType(APPROVED);
        study.setStudyLogoUrl("aStudyLogoUrl");
        study.setColorScheme(COLOR_SCHEME);
        study.setInstitutionId("anInstitutionId");
        study.setIrbProtocolId("anIrbProtocolId");
        study.setIrbProtocolName("anIrbName");
        study.setScheduleGuid("aScheduleGuid");
        study.setPhase(ANALYSIS);
        study.setDisease("subjective cognitive decline");
        study.setStudyDesignType("observational case control");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(study);
        assertEquals(node.size(), 24);
        assertEquals(node.get("identifier").textValue(), "oneId");
        assertEquals(node.get("name").textValue(), "name");
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertTrue(node.get("clientData").get("booleanFlag").booleanValue());
        assertEquals(node.get("clientData").get("stringValue").textValue(), "testString");
        assertEquals(node.get("clientData").get("intValue").intValue(), 4);
        assertEquals(node.get("version").longValue(), 3L);
        assertEquals(node.get("contacts").size(), 2);
        assertEquals(node.get("contacts").get(0).get("name").textValue(), "Name1");
        assertEquals(node.get("contacts").get(1).get("name").textValue(), "Name2");
        assertEquals(node.get("details").textValue(), "someDetails");
        assertEquals(node.get("irbName").textValue(), "WIRB");
        assertEquals(node.get("irbProtocolName").textValue(), "anIrbName");
        assertEquals(node.get("irbDecisionOn").textValue(), APPROVED_ON.toString());
        assertEquals(node.get("irbExpiresOn").textValue(), EXPIRES_ON.toString());
        assertEquals(node.get("irbDecisionType").textValue(), "approved");
        assertEquals(node.get("studyLogoUrl").textValue(), "aStudyLogoUrl");
        assertEquals(node.get("colorScheme").get("type").textValue(), "ColorScheme");
        assertEquals(node.get("institutionId").textValue(), "anInstitutionId");
        assertEquals(node.get("irbProtocolId").textValue(), "anIrbProtocolId");
        assertEquals(node.get("scheduleGuid").textValue(), "aScheduleGuid");
        assertEquals(node.get("phase").textValue(), "analysis");
        assertEquals(node.get("disease").textValue(), "subjective cognitive decline");
        assertEquals(node.get("studyDesignType").textValue(), "observational case control");
        assertEquals(node.get("logoURL").textValue(), TEST_LINK);
        assertNull(node.get("logoGuid"));
        assertEquals(node.get("type").textValue(), "Study");
        assertNull(node.get("studyId"));
        assertNull(node.get("appId"));
        
        Study deser = BridgeObjectMapper.get().readValue(node.toString(), Study.class);
        assertEquals(deser.getIdentifier(), "oneId");
        assertEquals(deser.getName(), "name");
        assertTrue(deser.isDeleted());
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertEquals(deser.getDetails(), "someDetails");
        assertEquals(deser.getIrbName(), "WIRB");
        assertEquals(deser.getIrbDecisionOn(), APPROVED_ON);
        assertEquals(deser.getIrbExpiresOn(), EXPIRES_ON);
        assertEquals(deser.getIrbDecisionType(), APPROVED);
        assertEquals(deser.getStudyLogoUrl(), "aStudyLogoUrl");
        assertEquals(deser.getColorScheme(), COLOR_SCHEME);
        assertEquals(deser.getInstitutionId(), "anInstitutionId");
        assertEquals(deser.getIrbProtocolId(), "anIrbProtocolId");
        assertEquals(deser.getIrbProtocolName(), "anIrbName");
        assertEquals(deser.getScheduleGuid(), "aScheduleGuid");
        assertEquals(deser.getContacts().size(), 2);
        assertEquals(deser.getContacts().get(0).getName(), "Name1");
        assertEquals(deser.getContacts().get(1).getName(), "Name2");
        assertEquals(deser.getPhase(), ANALYSIS);
        assertEquals(deser.getDisease(), "subjective cognitive decline");
        assertEquals(deser.getStudyDesignType(), "observational case control");
        assertEquals(deser.getLogoURL(), TEST_LINK);
        assertEquals(deser.getVersion(), new Long(3));
        
        JsonNode deserClientData = deser.getClientData();
        assertTrue(deserClientData.get("booleanFlag").booleanValue());
        assertEquals(deserClientData.get("stringValue").textValue(), "testString");
        assertEquals(deserClientData.get("intValue").intValue(), 4);

        ((ObjectNode)node).remove("id");
        ((ObjectNode)node).put("identifier", "oneId");
        deser = BridgeObjectMapper.get().readValue(node.toString(), Study.class);
        assertEquals(deser.getIdentifier(), "oneId");        
    }
}
