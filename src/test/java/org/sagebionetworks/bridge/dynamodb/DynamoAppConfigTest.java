package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoAppConfigTest {

    private static final String LABEL = "label";
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    private static final DateTime SURVEY_PUB_DATE = DateTime.now(DateTimeZone.UTC);
    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    private static final List<SurveyReference> SURVEY_REFS = ImmutableList.of(
            new SurveyReference("surveyA", BridgeUtils.generateGuid(), SURVEY_PUB_DATE),
            new SurveyReference("surveyB", BridgeUtils.generateGuid(), SURVEY_PUB_DATE));
    private static final List<SchemaReference> SCHEMA_REFS = ImmutableList.of(
            new SchemaReference("schemaA", 1),
            new SchemaReference("schemaB", 2));
    private static final List<ConfigReference> CONFIG_REFS = ImmutableList.of(
            new ConfigReference("config1", 1L),
            new ConfigReference("config2", 2L));
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl(TestUtils.randomName(DynamoAppConfigTest.class));
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoCriteria.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
                .verify();
    }
    
    @Test
    public void collectionsDoNotReturnNull() {
        AppConfig config = AppConfig.create();
        assertNotNull(config.getConfigElements());
        assertNotNull(config.getConfigReferences());
        assertNotNull(config.getSchemaReferences());
        assertNotNull(config.getSurveyReferences());
        
        config.setConfigElements(null);
        config.setConfigReferences(null);
        config.setSchemaReferences(null);
        config.setSurveyReferences(null);
        
        assertNotNull(config.getConfigElements());
        assertNotNull(config.getConfigReferences());
        assertNotNull(config.getSchemaReferences());
        assertNotNull(config.getSurveyReferences());
    }

    @Test
    public void canSerialize() throws Exception {
        Criteria criteria;
        criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setLanguage("fr");

        JsonNode clientData = TestUtils.getClientData();
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId(STUDY_ID.getIdentifier());
        appConfig.setLabel(LABEL);
        appConfig.setCriteria(criteria);
        appConfig.setCreatedOn(TIMESTAMP.getMillis());
        appConfig.setModifiedOn(TIMESTAMP.getMillis());
        appConfig.setSurveyReferences(SURVEY_REFS);
        appConfig.setSchemaReferences(SCHEMA_REFS);
        appConfig.setConfigReferences(CONFIG_REFS);
        appConfig.setConfigElements(ImmutableMap.of("config1", TestUtils.getClientData()));
        appConfig.setClientData(clientData);
        appConfig.setVersion(3L);
        appConfig.setDeleted(true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(appConfig);
        
        assertEquals(node.size(), 12);
        JsonNode critNode = node.get("criteria");
        assertEquals(critNode.get("language").textValue(), "fr");
        assertEquals(critNode.get("allOfGroups").size(), 2);
        assertEquals(critNode.get("allOfGroups").get(0).textValue(), "a");
        assertEquals(critNode.get("allOfGroups").get(1).textValue(), "b");
        assertEquals(critNode.get("noneOfGroups").size(), 2);
        assertEquals(critNode.get("noneOfGroups").get(0).textValue(), "d");
        assertEquals(critNode.get("noneOfGroups").get(1).textValue(), "c");
        assertEquals(critNode.get("minAppVersions").get("iPhone OS").intValue(), 2);
        assertEquals(critNode.get("minAppVersions").get("Android").intValue(), 10);
        assertEquals(critNode.get("maxAppVersions").get("iPhone OS").intValue(), 8);
        assertEquals(critNode.get("maxAppVersions").get("Android").intValue(), 15);
        assertEquals(critNode.get("type").textValue(), "Criteria");
        
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("modifiedOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("type").textValue(), "AppConfig");
        assertEquals(node.get("label").textValue(), LABEL);
        assertEquals(node.get("version").longValue(), 3L);
        assertEquals(node.get("clientData"), clientData);
        assertEquals(node.get("configElements").get("config1"), clientData);
        assertEquals(node.get("configElements").size(), 1);
        
        assertEquals(node.get("configReferences").size(), 2);
        assertEquals(node.get("configReferences").get(0).get("id").textValue(), "config1");
        assertEquals(node.get("configReferences").get(0).get("revision").longValue(), 1L);
        assertEquals(node.get("configReferences").get(1).get("id").textValue(), "config2");
        assertEquals(node.get("configReferences").get(1).get("revision").longValue(), 2L);
        
        assertEquals(node.get("schemaReferences").size(), 2);
        assertEquals(node.get("schemaReferences").get(0).get("id").textValue(), "schemaA");
        assertEquals(node.get("schemaReferences").get(0).get("revision").longValue(), 1L);
        assertEquals(node.get("schemaReferences").get(1).get("id").textValue(), "schemaB");
        assertEquals(node.get("schemaReferences").get(1).get("revision").longValue(), 2L);
        
        assertEquals(node.get("surveyReferences").size(), 2);
        assertEquals(node.get("surveyReferences").get(0).get("identifier").textValue(), "surveyA");
        assertEquals(node.get("surveyReferences").get(0).get("guid").textValue(),
                appConfig.getSurveyReferences().get(0).getGuid());
        assertEquals(node.get("surveyReferences").get(0).get("createdOn").textValue(), SURVEY_PUB_DATE.toString());
        assertEquals(node.get("surveyReferences").get(1).get("identifier").textValue(), "surveyB");
        assertEquals(node.get("surveyReferences").get(1).get("guid").textValue(),
                appConfig.getSurveyReferences().get(1).getGuid());
        assertEquals(node.get("surveyReferences").get(1).get("createdOn").textValue(), SURVEY_PUB_DATE.toString());
        
        AppConfig deser = BridgeObjectMapper.get().treeToValue(node, AppConfig.class);
        assertNull(deser.getStudyId());
        
        assertEquals(deser.getClientData(), clientData);
        assertEquals(deser.getConfigElements().get("config1"), clientData);
        assertEquals(deser.getCriteria(), appConfig.getCriteria());
        assertEquals(deser.getLabel(), appConfig.getLabel());
        assertEquals(deser.getSurveyReferences(), appConfig.getSurveyReferences());
        assertEquals(deser.getSchemaReferences(), appConfig.getSchemaReferences());
        assertEquals(deser.getConfigReferences(), appConfig.getConfigReferences());
        assertEquals(deser.getConfigElements(), appConfig.getConfigElements());
        assertEquals(deser.getCreatedOn(), appConfig.getCreatedOn());
        assertEquals(deser.getModifiedOn(), appConfig.getModifiedOn());
        assertEquals(deser.getGuid(), appConfig.getGuid());
        assertEquals(deser.getVersion(), appConfig.getVersion());
        assertEquals(deser.isDeleted(), appConfig.isDeleted());
    }
}
