package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

public class DynamoCompoundActivityDefinitionTest {
    private static final SchemaReference FOO_SCHEMA = new SchemaReference("foo", 2);
    private static final SchemaReference BAR_SCHEMA = new SchemaReference("bar", 3);
    private static final List<SchemaReference> SCHEMA_LIST = ImmutableList.of(FOO_SCHEMA, BAR_SCHEMA);

    private static final SurveyReference ASDF_SURVEY = new SurveyReference("asdf", "asdf-guid", null);
    private static final SurveyReference JKL_SURVEY = new SurveyReference("jkl", "jkl-guid", null);
    private static final List<SurveyReference> SURVEY_LIST = ImmutableList.of(ASDF_SURVEY, JKL_SURVEY);

    private static final String TASK_ID = "test-task";

    @Test
    public void getCompoundActivity() {
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        def.setTaskId(TASK_ID);

        CompoundActivity compoundActivity = def.getCompoundActivity();
        assertEquals(compoundActivity.getSchemaList(), SCHEMA_LIST);
        assertEquals(compoundActivity.getSurveyList(), SURVEY_LIST);
        assertEquals(compoundActivity.getTaskIdentifier(), TASK_ID);
    }

    @Test
    public void immutableLists() {
        // create def - Lists are initially empty.
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        assertTrue(def.getSchemaList().isEmpty());
        assertListIsImmutable(def.getSchemaList(), FOO_SCHEMA);
        assertTrue(def.getSurveyList().isEmpty());
        assertListIsImmutable(def.getSurveyList(), ASDF_SURVEY);

        // create test mutable lists
        List<SchemaReference> originalSchemaList = Lists.newArrayList(FOO_SCHEMA);
        List<SurveyReference> originalSurveyList = Lists.newArrayList(ASDF_SURVEY);

        // set to non-empty lists
        def.setSchemaList(originalSchemaList);
        def.setSurveyList(originalSurveyList);

        // modify original lists
        originalSchemaList.add(BAR_SCHEMA);
        originalSurveyList.add(JKL_SURVEY);

        // verify that the lists in the def are unchanged
        assertEquals(def.getSchemaList().size(), 1);
        assertEquals(def.getSchemaList().get(0), FOO_SCHEMA);
        assertListIsImmutable(def.getSchemaList(), BAR_SCHEMA);
        assertEquals(def.getSurveyList().size(), 1);
        assertEquals(def.getSurveyList().get(0), ASDF_SURVEY);
        assertListIsImmutable(def.getSurveyList(), JKL_SURVEY);

        // set lists to null, validate that lists are still empty and immutable
        def.setSchemaList(null);
        def.setSurveyList(null);
        assertTrue(def.getSchemaList().isEmpty());
        assertListIsImmutable(def.getSchemaList(), FOO_SCHEMA);
        assertTrue(def.getSurveyList().isEmpty());
        assertListIsImmutable(def.getSurveyList(), ASDF_SURVEY);
    }

    @Test
    public void serialize() throws Exception {
        // Use schema and survey refs so this test doesn't depend on those implementations.

        // start with JSON
        String jsonText = "{\n" +
                "   \"studyId\":\""+TEST_APP_ID+"\",\n" +
                "   \"taskId\":\"test-task\",\n" +
                "   \"schemaList\":[\n" +
                BridgeObjectMapper.get().writeValueAsString(FOO_SCHEMA) + ",\n" +
                BridgeObjectMapper.get().writeValueAsString(BAR_SCHEMA) + "\n" +
                "   ],\n" +
                "   \"surveyList\":[\n" +
                BridgeObjectMapper.get().writeValueAsString(ASDF_SURVEY) + ",\n" +
                BridgeObjectMapper.get().writeValueAsString(JKL_SURVEY) + "\n" +
                "   ],\n" +
                "   \"version\":42\n" +
                "}";

        // convert to POJO - deserialize it as the base type, so we know it works with the base type
        DynamoCompoundActivityDefinition def = (DynamoCompoundActivityDefinition) BridgeObjectMapper.get().readValue(
                jsonText, CompoundActivityDefinition.class);
        assertEquals(def.getStudyId(), TEST_APP_ID);
        assertEquals(def.getTaskId(), "test-task");
        assertEquals(def.getVersion().longValue(), 42);

        List<SchemaReference> outputSchemaList = def.getSchemaList();
        assertEquals(outputSchemaList.size(), 2);
        assertEquals(outputSchemaList.get(0), FOO_SCHEMA);
        assertEquals(outputSchemaList.get(1), BAR_SCHEMA);

        List<SurveyReference> outputSurveyList = def.getSurveyList();
        assertEquals(outputSurveyList.size(), 2);
        assertEquals(outputSurveyList.get(0), ASDF_SURVEY);
        assertEquals(outputSurveyList.get(1), JKL_SURVEY);

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(def, JsonNode.class);
        assertEquals(jsonNode.size(), 6);
        assertEquals(jsonNode.get("studyId").textValue(), TEST_APP_ID);
        assertEquals(jsonNode.get("taskId").textValue(), "test-task");
        assertEquals(jsonNode.get("version").intValue(), 42);
        assertEquals(jsonNode.get("type").textValue(), "CompoundActivityDefinition");

        // For the lists, this is already tested by the encapsulated classes. Just verify that we have a list of the
        // right size.
        assertTrue(jsonNode.get("schemaList").isArray());
        assertEquals(jsonNode.get("schemaList").size(), 2);

        assertTrue(jsonNode.get("surveyList").isArray());
        assertEquals(jsonNode.get("surveyList").size(), 2);

        // convert to JSON using the PUBLIC_DEFINITION_WRITER
        // For simplicity, just test that study ID is absent and the other major key values are present
        String publicJsonText = CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(def);
        JsonNode publicJsonNode = BridgeObjectMapper.get().readTree(publicJsonText);
        assertNull(publicJsonNode.get("studyId"));
        assertEquals(publicJsonNode.get("taskId").textValue(), "test-task");
        assertEquals(publicJsonNode.get("type").textValue(), "CompoundActivityDefinition");
    }

    private static <T> void assertListIsImmutable(List<T> list, T objToAdd) {
        try {
            list.add(objToAdd);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }
}
