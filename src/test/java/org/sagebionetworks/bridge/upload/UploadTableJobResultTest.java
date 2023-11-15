package org.sagebionetworks.bridge.upload;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadTableJobResultTest {
    private static final DateTime EXPIRES_ON = DateTime.parse("2015-11-17T03:15:37.409Z");
    private static final String JOB_GUID = "test-job-guid";
    private static final DateTime REQUESTED_ON = DateTime.parse("2015-11-10T14:30:06.233Z");
    private static final String URL = "https://example.com/";

    @Test
    public void fromJob() {
        // Make a job with the relevant fields.
        UploadTableJob job = UploadTableJob.create();
        job.setJobGuid(JOB_GUID);
        job.setStudyId(TestConstants.TEST_STUDY_ID);
        job.setRequestedOn(REQUESTED_ON);
        job.setStatus(UploadTableJob.Status.SUCCEEDED);

        // Convert to job result.
        UploadTableJobResult result = UploadTableJobResult.fromJob(job);
        assertEquals(result.getJobGuid(), JOB_GUID);
        assertEquals(result.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(result.getRequestedOn(), REQUESTED_ON);
        assertEquals(result.getStatus(), UploadTableJob.Status.SUCCEEDED);
    }

    @Test
    public void serialize() {
        // Start with Java object.
        UploadTableJobResult result = new UploadTableJobResult();
        result.setJobGuid(JOB_GUID);
        result.setStudyId(TestConstants.TEST_STUDY_ID);
        result.setRequestedOn(REQUESTED_ON);
        result.setStatus(UploadTableJob.Status.SUCCEEDED);
        result.setUrl(URL);
        result.setExpiresOn(EXPIRES_ON);

        // Convert to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(result, JsonNode.class);
        assertEquals(jsonNode.size(), 7);
        assertEquals(jsonNode.get("jobGuid").textValue(), JOB_GUID);
        assertEquals(jsonNode.get("studyId").textValue(), TestConstants.TEST_STUDY_ID);
        assertEquals(jsonNode.get("requestedOn").textValue(), REQUESTED_ON.toString());
        assertEquals(jsonNode.get("status").textValue(), "succeeded");
        assertEquals(jsonNode.get("url").textValue(), URL);
        assertEquals(jsonNode.get("expiresOn").textValue(), EXPIRES_ON.toString());
        assertEquals(jsonNode.get("type").textValue(), "UploadTableJobResult");

        // We never parse this from JSON, so we don't need to test deserialization.
    }
}
