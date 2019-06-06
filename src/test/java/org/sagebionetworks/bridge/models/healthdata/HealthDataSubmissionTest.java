package org.sagebionetworks.bridge.models.healthdata;

import static org.sagebionetworks.bridge.TestUtils.assertDatesWithTimeZoneEqual;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class HealthDataSubmissionTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String CREATED_ON_STR = "2017-08-24T14:38:57.340+0900";
    private static final DateTime CREATED_ON = DateTime.parse(CREATED_ON_STR);
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final String SURVEY_CREATED_ON_STR = "2017-09-07T15:02:56.756+0900";
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse(SURVEY_CREATED_ON_STR);
    private static final String SURVEY_GUID = "test-survey-guid";

    @Test
    public void serialization() throws Exception {
        // start with JSON
        // Note: This is technically not valid, since you can't specify both schema and survey. It will suffice for our
        // tests though.
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"" + CREATED_ON_STR + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STR + "\",\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"data\":{\n" +
                "       \"foo\":\"foo-value\",\n" +
                "       \"bar\":42\n" +
                "   },\n" +
                "   \"metadata\":{\n" +
                "       \"sample-metadata-key\":\"sample-metadata-value\"\n" +
                "   }\n" +
                "}";

        // convert to POJO
        HealthDataSubmission healthDataSubmission = BridgeObjectMapper.get().readValue(jsonText,
                HealthDataSubmission.class);
        assertEquals(healthDataSubmission.getAppVersion(), APP_VERSION);
        assertDatesWithTimeZoneEqual(CREATED_ON, healthDataSubmission.getCreatedOn());
        assertEquals(healthDataSubmission.getPhoneInfo(), PHONE_INFO);
        assertEquals(healthDataSubmission.getSchemaId(), SCHEMA_ID);
        assertEquals(healthDataSubmission.getSchemaRevision().intValue(), SCHEMA_REV);
        assertDatesWithTimeZoneEqual(SURVEY_CREATED_ON, healthDataSubmission.getSurveyCreatedOn());
        assertEquals(healthDataSubmission.getSurveyGuid(), SURVEY_GUID);

        JsonNode pojoData = healthDataSubmission.getData();
        assertEquals(pojoData.size(), 2);
        assertEquals(pojoData.get("foo").textValue(), "foo-value");
        assertEquals(pojoData.get("bar").intValue(), 42);

        JsonNode pojoMetadata = healthDataSubmission.getMetadata();
        assertEquals(pojoMetadata.size(), 1);
        assertEquals(pojoMetadata.get("sample-metadata-key").textValue(), "sample-metadata-value");

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(healthDataSubmission, JsonNode.class);
        assertEquals(jsonNode.size(), 10);
        assertEquals(jsonNode.get("appVersion").textValue(), APP_VERSION);
        assertDatesWithTimeZoneEqual(CREATED_ON, DateTime.parse(jsonNode.get("createdOn").textValue()));
        assertEquals(jsonNode.get("data"), pojoData);
        assertEquals(jsonNode.get("metadata"), pojoMetadata);
        assertEquals(jsonNode.get("phoneInfo").textValue(), PHONE_INFO);
        assertEquals(jsonNode.get("schemaId").textValue(), SCHEMA_ID);
        assertEquals(jsonNode.get("schemaRevision").intValue(), SCHEMA_REV);
        assertDatesWithTimeZoneEqual(SURVEY_CREATED_ON, DateTime.parse(jsonNode.get("surveyCreatedOn").textValue()));
        assertEquals(jsonNode.get("surveyGuid").textValue(), SURVEY_GUID);
        assertEquals(jsonNode.get("type").textValue(), "HealthDataSubmission");
    }
}
