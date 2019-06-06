package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleType;

public class HibernateSharedModuleMetadataTest {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final String MODULE_NOTES = "These are my notes for my module.";
    private static final String MODULE_OS = "Android";
    private static final Set<String> MODULE_TAGS = ImmutableSet.of("foo", "bar", "baz");
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;
    private static final String SURVEY_GUID = "test-survey-guid";

    private static final String SURVEY_CREATED_ON_STRING = "2017-04-05T20:54:53.625Z";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);

    @Test
    public void getModuleTypeSchema() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        assertEquals(metadata.getModuleType(), SharedModuleType.SCHEMA);
    }

    @Test
    public void getModuleTypeSurvey() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setSurveyCreatedOn(SURVEY_CREATED_ON_MILLIS);
        metadata.setSurveyGuid(SURVEY_GUID);
        assertEquals(metadata.getModuleType(), SharedModuleType.SURVEY);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void getModuleTypeNeither() {
        SharedModuleMetadata.create().getModuleType();
    }

    @Test
    public void tags() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();

        // tags starts out empty
        assertTrue(metadata.getTags().isEmpty());

        // set the tags
        metadata.setTags(ImmutableSet.of("foo", "bar", "baz"));
        assertEquals(metadata.getTags(), ImmutableSet.of("foo", "bar", "baz"));

        // Set tag set to null. It should come back as empty.
        metadata.setTags(null);
        assertTrue(metadata.getTags().isEmpty());
    }

    @Test
    public void jsonSerializationWithOptionalFields() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"" + MODULE_ID + "\",\n" +
                "   \"licenseRestricted\":true,\n" +
                "   \"name\":\"" + MODULE_NAME + "\",\n" +
                "   \"notes\":\"" + MODULE_NOTES + "\",\n" +
                "   \"os\":\"" + MODULE_OS + "\",\n" +
                "   \"published\":true,\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"tags\":[\"foo\", \"bar\", \"baz\"],\n" +
                "   \"delete\":false,\n" +
                "   \"version\":" + MODULE_VERSION + "\n" +
                "}";

        // Convert to POJO
        SharedModuleMetadata metadata = BridgeObjectMapper.get().readValue(jsonText, SharedModuleMetadata.class);
        assertEquals(metadata.getId(), MODULE_ID);
        assertTrue(metadata.isLicenseRestricted());
        assertEquals(metadata.getName(), MODULE_NAME);
        assertEquals(metadata.getNotes(), MODULE_NOTES);
        assertEquals(metadata.getOs(), MODULE_OS);
        assertTrue(metadata.isPublished());
        assertEquals(metadata.getSchemaId(), SCHEMA_ID);
        assertEquals(metadata.getSchemaRevision().intValue(), SCHEMA_REV);
        assertEquals(metadata.getTags(), MODULE_TAGS);
        assertFalse(metadata.isDeleted());
        assertEquals(metadata.getVersion(), MODULE_VERSION);

        // Convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(metadata, JsonNode.class);
        assertEquals(jsonNode.size(), 13);
        assertEquals(jsonNode.get("id").textValue(), MODULE_ID);
        assertTrue(jsonNode.get("licenseRestricted").booleanValue());
        assertEquals(jsonNode.get("name").textValue(), MODULE_NAME);
        assertEquals(jsonNode.get("notes").textValue(), MODULE_NOTES);
        assertEquals(jsonNode.get("os").textValue(), MODULE_OS);
        assertTrue(jsonNode.get("published").booleanValue());
        assertEquals(jsonNode.get("schemaId").textValue(), SCHEMA_ID);
        assertEquals(jsonNode.get("schemaRevision").intValue(), SCHEMA_REV);
        assertEquals(jsonNode.get("version").intValue(), MODULE_VERSION);
        assertEquals(jsonNode.get("moduleType").textValue(), "schema");
        assertFalse(jsonNode.get("deleted").booleanValue());
        assertEquals(jsonNode.get("type").textValue(), "SharedModuleMetadata");

        JsonNode tagsNode = jsonNode.get("tags");
        assertEquals(tagsNode.size(), 3);
        Set<String> jsonNodeTags = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String oneTag = tagsNode.get(i).textValue();
            jsonNodeTags.add(oneTag);
        }
        assertEquals(jsonNodeTags, MODULE_TAGS);
    }

    @Test
    public void jsonSerializationWithSurvey() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"" + MODULE_ID + "\",\n" +
                "   \"name\":\"" + MODULE_NAME + "\",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STRING + "\",\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"version\":" + MODULE_VERSION + ",\n" +
                "   \"deleted\":true\n" +
                "}";

        // Convert to POJO - Test only the fields we set, so that we don't have exploding tests.
        SharedModuleMetadata metadata = BridgeObjectMapper.get().readValue(jsonText, SharedModuleMetadata.class);
        assertEquals(metadata.getId(), MODULE_ID);
        assertEquals(metadata.getName(), MODULE_NAME);
        assertEquals(metadata.getSurveyCreatedOn().longValue(), SURVEY_CREATED_ON_MILLIS);
        assertEquals(metadata.getSurveyGuid(), SURVEY_GUID);
        assertEquals(metadata.getVersion(), MODULE_VERSION);

        // Convert back to JSON. licenseRestricted and published default to false. tags defaults to empty set.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(metadata, JsonNode.class);
        assertEquals(jsonNode.size(), 11);
        assertEquals(jsonNode.get("id").textValue(), MODULE_ID);
        assertFalse(jsonNode.get("licenseRestricted").booleanValue());
        assertEquals(jsonNode.get("name").textValue(), MODULE_NAME);
        assertFalse(jsonNode.get("published").booleanValue());
        assertEquals(jsonNode.get("surveyGuid").textValue(), SURVEY_GUID);
        assertTrue(jsonNode.get("tags").isArray());
        assertEquals(jsonNode.get("tags").size(), 0);
        assertEquals(jsonNode.get("version").intValue(), MODULE_VERSION);
        assertEquals(jsonNode.get("moduleType").textValue(), "survey");
        assertTrue(jsonNode.get("deleted").booleanValue());
        assertEquals(jsonNode.get("type").textValue(), "SharedModuleMetadata");

        String surveyCreatedOnString = jsonNode.get("surveyCreatedOn").textValue();
        assertEquals(DateUtils.convertToMillisFromEpoch(surveyCreatedOnString), SURVEY_CREATED_ON_MILLIS);
    }
}
