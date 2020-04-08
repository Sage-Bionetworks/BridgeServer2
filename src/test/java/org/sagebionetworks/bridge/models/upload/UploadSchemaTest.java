package org.sagebionetworks.bridge.models.upload;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.AppVersionHelper;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

@SuppressWarnings("unchecked")
public class UploadSchemaTest {
    private static final String MODULE_ID = "test-schema-module";
    private static final int MODULE_VERSION = 3;

    @Test
    public void fieldDefList() {
        UploadSchema schema = UploadSchema.create();

        // make field for test
        UploadFieldDefinition fieldDef1 = new UploadFieldDefinition.Builder().withName("test-field-1")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build();
        UploadFieldDefinition fieldDef2 = new UploadFieldDefinition.Builder().withName("test-field-2")
                .withType(UploadFieldType.INT).build();

        // field def list starts out empty
        assertTrue(schema.getFieldDefinitions().isEmpty());
        assertFieldDefListIsImmutable(schema.getFieldDefinitions());

        // set the field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(fieldDef1);

        schema.setFieldDefinitions(fieldDefList);
        assertFieldDefListIsImmutable(schema.getFieldDefinitions());
        {
            List<UploadFieldDefinition> gettedFieldDefList = schema.getFieldDefinitions();
            assertEquals(gettedFieldDefList.size(), 1);
            assertEquals(gettedFieldDefList.get(0), fieldDef1);
        }

        // Modify the original list. getFieldDefinitions() shouldn't reflect this change.
        fieldDefList.add(fieldDef2);
        {
            List<UploadFieldDefinition> gettedFieldDefList = schema.getFieldDefinitions();
            assertEquals(gettedFieldDefList.size(), 1);
            assertEquals(gettedFieldDefList.get(0), fieldDef1);
        }

        // Set field def list to null. It'll come back as empty.
        schema.setFieldDefinitions(null);
        assertTrue(schema.getFieldDefinitions().isEmpty());
        assertFieldDefListIsImmutable(schema.getFieldDefinitions());
    }

    private static void assertFieldDefListIsImmutable(List<UploadFieldDefinition> fieldDefList) {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("added-field")
                .withType(UploadFieldType.BOOLEAN).build();
        try {
            fieldDefList.add(fieldDef);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    @Test
    public void getKeyFromStudyAndSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(API_APP_ID);
        ddbUploadSchema.setSchemaId("test");
        assertEquals(ddbUploadSchema.getKey(), "api:test");
    }

    @Test
    public void getKeyFromNullStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setSchemaId("test");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromEmptyStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("");
        ddbUploadSchema.setSchemaId("test");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromBlankStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId("   ");
        ddbUploadSchema.setSchemaId("test");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromNullSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(API_APP_ID);
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromEmptySchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(API_APP_ID);
        ddbUploadSchema.setSchemaId("");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getKeyFromBlankSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(API_APP_ID);
        ddbUploadSchema.setSchemaId("   ");
        assertNull(ddbUploadSchema.getKey());
    }

    @Test
    public void getStudyAndSchemaFromKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey(API_APP_ID + ":test");
        assertEquals(ddbUploadSchema.getStudyId(), API_APP_ID);
        assertEquals(ddbUploadSchema.getSchemaId(), "test");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void nullKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emptyKey() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void keyWithOnePart() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("keyWithOnePart");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void keyWithEmptyStudy() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey(":test");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void keyWithEmptySchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api:");
    }

    @Test
    public void getKeyWithColonsInSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setStudyId(API_APP_ID);
        ddbUploadSchema.setSchemaId("test:schema");
        assertEquals(ddbUploadSchema.getKey(), "api:test:schema");
    }

    @Test
    public void setKeyWithColonsInSchema() {
        DynamoUploadSchema ddbUploadSchema = new DynamoUploadSchema();
        ddbUploadSchema.setKey("api:test:schema");
        assertEquals(ddbUploadSchema.getStudyId(), API_APP_ID);
        assertEquals(ddbUploadSchema.getSchemaId(), "test:schema");
    }

    @Test
    public void getSetMinMaxAppVersions() throws Exception {
        AppVersionHelper.testAppVersionHelper(DynamoUploadSchema.class);
    }

    @Test
    public void schemaKeyObject() {
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setStudyId("test-study");
        schema.setSchemaId("test-schema");
        schema.setRevision(7);
        assertEquals(schema.getSchemaKey().toString(), "test-study-test-schema-v7");
    }

    @Test
    public void testSerialization() throws Exception {
        String surveyCreatedOnStr = "2016-04-27T19:00:00.002-0700";
        long surveyCreatedOnMillis = DateTime.parse(surveyCreatedOnStr).getMillis();

        // start with JSON. Some field definitions may already be serialized using upper-case enums
        // so leave this test string as it is. We know from other tests that lower-case 
        // strings work.
        String jsonText = "{\n" +
                "   \"maxAppVersions\":{\"iOS\":37, \"Android\":42},\n" +
                "   \"minAppVersions\":{\"iOS\":13, \"Android\":23},\n" +
                "   \"moduleId\":\"" + MODULE_ID + "\",\n" +
                "   \"moduleVersion\":" + MODULE_VERSION + ",\n" +
                "   \"name\":\"Test Schema\",\n" +
                "   \"revision\":3,\n" +
                "   \"schemaId\":\"test-schema\",\n" +
                "   \"schemaType\":\"ios_survey\",\n" +
                "   \"studyId\":\"test-study\",\n" +
                "   \"deleted\":true,\n"+
                "   \"surveyGuid\":\"survey-guid\",\n" +
                "   \"surveyCreatedOn\":\"" + surveyCreatedOnStr + "\",\n" +
                "   \"version\":6,\n" +
                "   \"fieldDefinitions\":[\n" +
                "       {\n" +
                "           \"name\":\"foo\",\n" +
                "           \"required\":true,\n" +
                "           \"type\":\"INT\"\n" +
                "       },\n" +
                "       {\n" +
                "           \"name\":\"bar\",\n" +
                "           \"required\":false,\n" +
                "           \"type\":\"STRING\"\n" +
                "       }\n" +
                "   ]\n" +
                "}";

        // convert to POJO
        UploadSchema uploadSchema = BridgeObjectMapper.get().readValue(jsonText, UploadSchema.class);
        assertEquals(uploadSchema.getModuleId(), MODULE_ID);
        assertEquals(uploadSchema.getModuleVersion().intValue(), MODULE_VERSION);
        assertEquals(uploadSchema.getName(), "Test Schema");
        assertEquals(uploadSchema.getRevision(), 3);
        assertEquals(uploadSchema.getSchemaId(), "test-schema");
        assertEquals(uploadSchema.getSchemaType(), UploadSchemaType.IOS_SURVEY);
        assertEquals(uploadSchema.getStudyId(), "test-study");
        assertEquals(uploadSchema.getSurveyGuid(), "survey-guid");
        assertEquals(uploadSchema.getSurveyCreatedOn().longValue(), surveyCreatedOnMillis);
        assertTrue(uploadSchema.isDeleted());
        assertEquals(((DynamoUploadSchema) uploadSchema).getVersion().longValue(), 6);

        assertEquals(uploadSchema.getAppVersionOperatingSystems(), ImmutableSet.of("iOS", "Android"));
        assertEquals(uploadSchema.getMinAppVersion("iOS").intValue(), 13);
        assertEquals(uploadSchema.getMaxAppVersion("iOS").intValue(), 37);
        assertEquals(uploadSchema.getMinAppVersion("Android").intValue(), 23);
        assertEquals(uploadSchema.getMaxAppVersion("Android").intValue(), 42);

        UploadFieldDefinition fooFieldDef = uploadSchema.getFieldDefinitions().get(0);
        assertEquals(fooFieldDef.getName(), "foo");
        assertTrue(fooFieldDef.isRequired());
        assertEquals(fooFieldDef.getType(), UploadFieldType.INT);

        UploadFieldDefinition barFieldDef = uploadSchema.getFieldDefinitions().get(1);
        assertEquals(barFieldDef.getName(), "bar");
        assertFalse(barFieldDef.isRequired());
        assertEquals(barFieldDef.getType(), UploadFieldType.STRING);

        // Add study ID and verify that it doesn't get leaked into the JSON
        uploadSchema.setStudyId("test-study");

        // convert back to JSON - Note that we do this weird thing converting it to a string then reading it into a
        // JsonNode. This is because elsewhere, when we test PUBLIC_SCHEMA_WRITER, we have to do the same thing, and
        // Jackson has a weirdness where depending on how you convert it, you might get an IntNode or a LongNode. So
        // for consistency in tests, we should do it the same way every time.
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(uploadSchema);
        JsonNode jsonNode = BridgeObjectMapper.get().readTree(convertedJson);
        assertEquals(jsonNode.size(), 15);
        assertEquals(jsonNode.get("moduleId").textValue(), MODULE_ID);
        assertEquals(jsonNode.get("moduleVersion").intValue(), MODULE_VERSION);
        assertEquals(jsonNode.get("name").textValue(), "Test Schema");
        assertEquals(jsonNode.get("revision").intValue(), 3);
        assertEquals(jsonNode.get("schemaId").textValue(), "test-schema");
        assertEquals(jsonNode.get("schemaType").textValue(), "ios_survey");
        assertEquals(jsonNode.get("studyId").textValue(), "test-study");
        assertEquals(jsonNode.get("surveyGuid").textValue(), "survey-guid");
        assertEquals(jsonNode.get("type").textValue(), "UploadSchema");
        assertTrue(jsonNode.get("deleted").booleanValue());
        assertEquals(jsonNode.get("version").intValue(), 6);
        assertTrue(jsonNode.get("deleted").booleanValue());

        JsonNode maxAppVersionMap = jsonNode.get("maxAppVersions");
        assertEquals(maxAppVersionMap.size(), 2);
        assertEquals(maxAppVersionMap.get("iOS").intValue(), 37);
        assertEquals(maxAppVersionMap.get("Android").intValue(), 42);

        JsonNode minAppVersionMap = jsonNode.get("minAppVersions");
        assertEquals(minAppVersionMap.size(), 2);
        assertEquals(minAppVersionMap.get("iOS").intValue(), 13);
        assertEquals(minAppVersionMap.get("Android").intValue(), 23);

        // The createdOn time is converted into ISO timestamp, but might be in a different timezone. Ensure that it
        // still refers to the correct instant in time, down to the millisecond.
        long resultSurveyCreatedOnMillis = DateTime.parse(jsonNode.get("surveyCreatedOn").textValue()).getMillis();
        assertEquals(resultSurveyCreatedOnMillis, surveyCreatedOnMillis);

        JsonNode fieldDefJsonList = jsonNode.get("fieldDefinitions");
        assertEquals(fieldDefJsonList.size(), 2);

        JsonNode fooJsonMap = fieldDefJsonList.get(0);
        assertEquals(fooJsonMap.get("name").textValue(), "foo");
        assertTrue(fooJsonMap.get("required").booleanValue());
        assertEquals(fooJsonMap.get("type").textValue(), "int");

        JsonNode barJsonMap = fieldDefJsonList.get(1);
        assertEquals(barJsonMap.get("name").textValue(), "bar");
        assertFalse(barJsonMap.get("required").booleanValue());
        assertEquals(barJsonMap.get("type").textValue(), "string");

        // Serialize it again using the public writer, which includes all fields except studyId.
        String publicJson = UploadSchema.PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchema);
        JsonNode publicJsonNode = BridgeObjectMapper.get().readTree(publicJson);

        // Public JSON is missing studyId, but is otherwise identical to the non-public (internal worker) JSON.
        assertFalse(publicJsonNode.has("studyId"));
        ((ObjectNode) publicJsonNode).put("studyId", "test-study");
        assertEquals(Sets.newHashSet(publicJsonNode.fieldNames()), Sets.newHashSet(jsonNode.fieldNames()));
    }

    @Test
    public void testDynamoDbFieldDefListMarshaller() throws Exception {
        DynamoUploadSchema.FieldDefinitionListMarshaller fieldDefListMarshaller =
                new DynamoUploadSchema.FieldDefinitionListMarshaller();

        // start with JSON
        String jsonText = "[\n" +
                "   {\n" +
                "       \"name\":\"foo\",\n" +
                "       \"required\":true,\n" +
                "       \"type\":\"INT\"\n" +
                "   },\n" +
                "   {\n" +
                "       \"name\":\"bar\",\n" +
                "       \"required\":false,\n" +
                "       \"type\":\"STRING\"\n" +
                "   }\n" +
                "]";

        // unmarshal and validate
        // Note that the first argument is supposed to be of type Class<List<UploadFileDefinition>>. Unfortunately,
        // there is no way to actually create a class of that type. Fortunately, the unmarshaller never uses that
        // object, so we just pass in null.
        List<UploadFieldDefinition> fieldDefList = fieldDefListMarshaller.unconvert(jsonText);
        assertEquals(fieldDefList.size(), 2);

        UploadFieldDefinition fooFieldDef = fieldDefList.get(0);
        assertEquals(fooFieldDef.getName(), "foo");
        assertTrue(fooFieldDef.isRequired());
        assertEquals(fooFieldDef.getType(), UploadFieldType.INT);

        UploadFieldDefinition barFieldDef = fieldDefList.get(1);
        assertEquals(barFieldDef.getName(), "bar");
        assertFalse(barFieldDef.isRequired());
        assertEquals(barFieldDef.getType(), UploadFieldType.STRING);

        // re-marshall
        String marshalledJson = fieldDefListMarshaller.convert(fieldDefList);

        // then convert to a list so we can validate the raw JSON
        List<Map<String, Object>> fieldDefJsonList = BridgeObjectMapper.get().readValue(marshalledJson,
                List.class);
        assertEquals(fieldDefJsonList.size(), 2);

        Map<String, Object> fooJsonMap = fieldDefJsonList.get(0);
        assertEquals(fooJsonMap.get("name"), "foo");
        assertTrue((boolean) fooJsonMap.get("required"));
        assertEquals(fooJsonMap.get("type"), "int");

        Map<String, Object> barJsonMap = fieldDefJsonList.get(1);
        assertEquals(barJsonMap.get("name"), "bar");
        assertFalse((boolean) barJsonMap.get("required"));
        assertEquals(barJsonMap.get("type"), "string");
    }
}
