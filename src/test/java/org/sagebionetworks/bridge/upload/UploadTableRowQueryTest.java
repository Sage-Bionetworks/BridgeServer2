package org.sagebionetworks.bridge.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadTableRowQueryTest {
    @Test
    public void deserialize() throws JsonProcessingException {
        // We only ever deserialize this from JSON, so just test that it works.
        String jsonText = "{\n" +
                "   \"appId\":\"test-app\",\n" +
                "   \"studyId\":\"test-study\",\n" +
                "   \"assessmentGuid\":\"test-assessment\",\n" +
                "   \"startTime\":\"2018-05-01T00:00:00.000Z\",\n" +
                "   \"endTime\":\"2018-05-02T00:00:00.000Z\",\n" +
                "   \"includeTestData\":true,\n" +
                "   \"start\":10,\n" +
                "   \"pageSize\":50\n" +
                "}";

        UploadTableRowQuery query = BridgeObjectMapper.get().readValue(jsonText, UploadTableRowQuery.class);
        assertEquals(query.getAppId(), "test-app");
        assertEquals(query.getStudyId(), "test-study");
        assertEquals(query.getAssessmentGuid(), "test-assessment");
        assertEquals(query.getStartTime().toString(), "2018-05-01T00:00:00.000Z");
        assertEquals(query.getEndTime().toString(), "2018-05-02T00:00:00.000Z");
        assertTrue(query.getIncludeTestData());
        assertEquals(query.getStart().intValue(), 10);
        assertEquals(query.getPageSize().intValue(), 50);
    }
}
