package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.upload.UploadTableJob;

public class HibernateUploadTableJobTest {
    private static final String JOB_GUID = "test-job-guid";
    private static final String S3_KEY = "dummy-s3-key";

    @Test
    public void serialize() {
        // Start with Java object.
        UploadTableJob job = UploadTableJob.create();
        job.setJobGuid(JOB_GUID);
        job.setAppId(TestConstants.TEST_APP_ID);
        job.setStudyId(TestConstants.TEST_STUDY_ID);
        job.setRequestedOn(TestConstants.TIMESTAMP);
        job.setStatus(UploadTableJob.Status.SUCCEEDED);
        job.setS3Key(S3_KEY);

        // Convert to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(job, JsonNode.class);
        assertEquals(jsonNode.size(), 7);
        assertEquals(jsonNode.get("jobGuid").textValue(), JOB_GUID);
        assertEquals(jsonNode.get("appId").textValue(), TestConstants.TEST_APP_ID);
        assertEquals(jsonNode.get("studyId").textValue(), TestConstants.TEST_STUDY_ID);
        assertEquals(jsonNode.get("requestedOn").textValue(), TestConstants.TIMESTAMP.toString());
        assertEquals(jsonNode.get("status").textValue(), "succeeded");
        assertEquals(jsonNode.get("s3Key").textValue(), S3_KEY);
        assertEquals(jsonNode.get("type").textValue(), "UploadTableJob");

        // Convert back to Java object.
        job = BridgeObjectMapper.get().convertValue(jsonNode, UploadTableJob.class);
        assertEquals(job.getJobGuid(), JOB_GUID);
        assertEquals(job.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(job.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(job.getRequestedOn(), TestConstants.TIMESTAMP);
        assertEquals(job.getStatus(), UploadTableJob.Status.SUCCEEDED);
        assertEquals(job.getS3Key(), S3_KEY);
    }
}
