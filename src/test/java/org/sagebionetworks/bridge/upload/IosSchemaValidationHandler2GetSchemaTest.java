package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class IosSchemaValidationHandler2GetSchemaTest {
    private static final Map<String, Map<String, Integer>> DEFAULT_SCHEMA_REV_MAP =
            ImmutableMap.of(API_APP_ID, ImmutableMap.of("schema-rev-test", 2));

    private static final String TEST_SURVEY_CREATED_ON_STRING = "2015-08-27T13:38:55-07:00";
    private static final long TEST_SURVEY_CREATED_ON_MILLIS = DateTime.parse(TEST_SURVEY_CREATED_ON_STRING)
            .getMillis();

    @Test
    public void survey() {
        // mock survey service
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");
        survey.setSchemaRevision(4);

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(eq(TEST_STUDY),
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS)), eq(false),
                eq(true))).thenReturn(survey);

        // mock upload schema service
        UploadSchema dummySchema = UploadSchema.create();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRevNoThrow(TEST_STUDY, "test-survey", 4)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertSame(retVal, dummySchema);
    }

    @Test
    public void surveyWithNoIdentifier() {
        // mock survey service
        DynamoSurvey survey = new DynamoSurvey();
        survey.setSchemaRevision(4);

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(eq(TEST_STUDY),
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS)), eq(false),
                eq(true))).thenReturn(survey);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // Execute. Returns null.
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertNull(retVal);
    }

    @Test
    public void surveyWithNoSchemaRev() {
        // mock survey service
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(eq(TEST_STUDY),
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS)), eq(false),
                eq(true))).thenReturn(survey);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // Execute. Returns null.
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertNull(retVal);
    }

    @Test
    public void surveySchemaNotFound() {
        // Mock survey service.
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("test-survey");
        survey.setSchemaRevision(4);

        SurveyService mockSurveyService = mock(SurveyService.class);
        when(mockSurveyService.getSurvey(eq(TEST_STUDY),
                eq(new GuidCreatedOnVersionHolderImpl("test-guid", TEST_SURVEY_CREATED_ON_MILLIS)), eq(false),
                eq(true))).thenReturn(survey);

        // Set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setSurveyService(mockSurveyService);
        handler.setUploadSchemaService(mock(UploadSchemaService.class));

        // Make input.
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("surveyGuid", "test-guid");
        infoJson.put("surveyCreatedOn", TEST_SURVEY_CREATED_ON_STRING);

        // Execute. Returns null.
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertNull(retVal);
    }

    @Test
    public void itemWithDefaultRev() {
        // mock upload schema service
        UploadSchema dummySchema = UploadSchema.create();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRevNoThrow(TEST_STUDY, "test-schema", 1)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "test-schema");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertSame(retVal, dummySchema);
    }

    @Test
    public void itemWithLegacyMapRev() {
        // mock upload schema service
        UploadSchema dummySchema = UploadSchema.create();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRevNoThrow(TEST_STUDY, "schema-rev-test", 2)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertSame(retVal, dummySchema);
    }

    @Test
    public void itemWithRev() {
        // mock upload schema service
        UploadSchema dummySchema = UploadSchema.create();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRevNoThrow(TEST_STUDY, "schema-rev-test", 3)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");
        infoJson.put("schemaRevision", 3);

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertSame(retVal, dummySchema);
    }

    @Test
    public void fallbackToIdentifier() {
        // mock upload schema service
        UploadSchema dummySchema = UploadSchema.create();
        UploadSchemaService mockSchemaSvc = mock(UploadSchemaService.class);
        when(mockSchemaSvc.getUploadSchemaByIdAndRevNoThrow(TEST_STUDY, "test-schema", 1)).thenReturn(
                dummySchema);

        // set up test handler
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mockSchemaSvc);

        // make input
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("identifier", "test-schema");

        // execute and validate
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertSame(retVal, dummySchema);
    }

    @Test
    public void schemaNotFound() {
        // Set up test handler.
        IosSchemaValidationHandler2 handler = new IosSchemaValidationHandler2();
        handler.setDefaultSchemaRevisionMap(DEFAULT_SCHEMA_REV_MAP);
        handler.setUploadSchemaService(mock(UploadSchemaService.class));

        // Make input.
        ObjectNode infoJson = BridgeObjectMapper.get().createObjectNode();
        infoJson.put("item", "schema-rev-test");
        infoJson.put("schemaRevision", 3);

        // Execute. Returns null.
        UploadSchema retVal = handler.getUploadSchema(TEST_STUDY, infoJson);
        assertNull(retVal);
    }

    @Test
    public void schemaless() {
        UploadSchema retVal = new IosSchemaValidationHandler2().getUploadSchema(TEST_STUDY,
                BridgeObjectMapper.get().createObjectNode());
        assertNull(retVal);
    }
}
