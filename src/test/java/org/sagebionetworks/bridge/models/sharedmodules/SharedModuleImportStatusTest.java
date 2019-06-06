package org.sagebionetworks.bridge.models.sharedmodules;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;

public class SharedModuleImportStatusTest {
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;
    private static final String SURVEY_GUID = "test-survey-guid";

    private static final String SURVEY_CREATED_ON_STRING = "2017-04-05T20:54:53.625Z";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);

    @Test(expectedExceptions = NullPointerException.class)
    public void nullSchemaId() {
        new SharedModuleImportStatus(null, SCHEMA_REV);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emptySchemaId() {
        new SharedModuleImportStatus("", SCHEMA_REV);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void blankSchemaId() {
        new SharedModuleImportStatus("   ", SCHEMA_REV);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeSchemaRev() {
        new SharedModuleImportStatus(SCHEMA_ID, -1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void zeroSchemaRev() {
        new SharedModuleImportStatus(SCHEMA_ID, 0);
    }

    @Test
    public void schemaModule() {
        // Test constructor and getters.
        SharedModuleImportStatus status = new SharedModuleImportStatus(SCHEMA_ID, SCHEMA_REV);
        assertEquals(status.getModuleType(), SharedModuleType.SCHEMA);
        assertEquals(status.getSchemaId(), SCHEMA_ID);
        assertEquals(status.getSchemaRevision().intValue(), SCHEMA_REV);
        assertNull(status.getSurveyCreatedOn());
        assertNull(status.getSurveyGuid());

        // Test JSON serialization. We only ever write this to JSON, never read it from JSON, so we only need to test
        // this one way.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(status, JsonNode.class);
        assertEquals(jsonNode.size(), 4);
        assertEquals(jsonNode.get("moduleType").textValue(), "schema");
        assertEquals(jsonNode.get("schemaId").textValue(), SCHEMA_ID);
        assertEquals(jsonNode.get("schemaRevision").intValue(), SCHEMA_REV);
        assertEquals(jsonNode.get("type").textValue(), "SharedModuleImportStatus");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void nullSurveyGuid() {
        new SharedModuleImportStatus(null, SURVEY_CREATED_ON_MILLIS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emptySurveyGuid() {
        new SharedModuleImportStatus("", SURVEY_CREATED_ON_MILLIS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void blankSurveyGuid() {
        new SharedModuleImportStatus("   ", SURVEY_CREATED_ON_MILLIS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeSurveyCreatedOn() {
        new SharedModuleImportStatus(SURVEY_GUID, -1L);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void zeroSurveyCreatedOn() {
        new SharedModuleImportStatus(SURVEY_GUID, 0L);
    }

    @Test
    public void surveyModule() {
        // Test constructor and getters.
        SharedModuleImportStatus status = new SharedModuleImportStatus(SURVEY_GUID, SURVEY_CREATED_ON_MILLIS);
        assertEquals(status.getModuleType(), SharedModuleType.SURVEY);
        assertNull(status.getSchemaId());
        assertNull(status.getSchemaRevision());
        assertEquals(status.getSurveyGuid(), SURVEY_GUID);
        assertEquals(status.getSurveyCreatedOn().longValue(), SURVEY_CREATED_ON_MILLIS);

        // Test JSON serialization.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(status, JsonNode.class);
        assertEquals(jsonNode.size(), 4);
        assertEquals(jsonNode.get("moduleType").textValue(), "survey");
        assertEquals(jsonNode.get("surveyCreatedOn").textValue(), SURVEY_CREATED_ON_STRING);
        assertEquals(jsonNode.get("surveyGuid").textValue(), SURVEY_GUID);
        assertEquals(jsonNode.get("type").textValue(), "SharedModuleImportStatus");
    }
}
