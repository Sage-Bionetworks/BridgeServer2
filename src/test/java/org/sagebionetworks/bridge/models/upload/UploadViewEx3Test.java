package org.sagebionetworks.bridge.models.upload;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadataView;

public class UploadViewEx3Test {
    private static final String INSTANCE_GUID = "dummy-instance-guid";
    private static final String UPLOAD_ID = "dummy-upload-id";

    @Test
    public void serialize() throws Exception {
        // Make simple AdherenceRecord, HealthDataRecordEx3, TimelineMetadata, and Upload. Just choose a small set of
        // attributes. Full JSON serialization of these classes is tested elsewhere.
        AdherenceRecord adherenceRecordForSchedule = new AdherenceRecord();
        adherenceRecordForSchedule.setInstanceGuid(INSTANCE_GUID);

        AdherenceRecord adherenceRecordForUpload = new AdherenceRecord();
        adherenceRecordForUpload.addUploadId(UPLOAD_ID);

        HealthDataRecordEx3 healthDataRecord = HealthDataRecordEx3.create();
        healthDataRecord.setId(UPLOAD_ID);

        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setAssessmentInstanceGuid(INSTANCE_GUID);
        TimelineMetadataView timelineMetadataView = new TimelineMetadataView(timelineMetadata);

        Upload upload = Upload.create();
        upload.setUploadId(UPLOAD_ID);

        // Make UploadViewEx3.
        UploadViewEx3 uploadView = new UploadViewEx3();
        uploadView.setId(UPLOAD_ID);
        uploadView.setHealthCode(HEALTH_CODE);
        uploadView.setUserId(TEST_USER_ID);
        uploadView.setAdherenceRecordsForSchedule(ImmutableList.of(adherenceRecordForSchedule));
        uploadView.setAdherenceRecordsForUpload(ImmutableList.of(adherenceRecordForUpload));
        uploadView.setRecord(healthDataRecord);
        uploadView.setTimelineMetadata(timelineMetadataView);
        uploadView.setUpload(upload);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(uploadView, JsonNode.class);
        assertEquals(jsonNode.size(), 9);
        assertEquals(jsonNode.get("id").textValue(), UPLOAD_ID);
        assertEquals(jsonNode.get("healthCode").textValue(), HEALTH_CODE);
        assertEquals(jsonNode.get("userId").textValue(), TEST_USER_ID);
        assertEquals(jsonNode.get("adherenceRecordsForSchedule").get(0).get("instanceGuid").textValue(), INSTANCE_GUID);
        assertEquals(jsonNode.get("adherenceRecordsForUpload").get(0).get("uploadIds").get(0).textValue(), UPLOAD_ID);
        assertEquals(jsonNode.get("record").get("id").textValue(), UPLOAD_ID);
        assertEquals(jsonNode.get("timelineMetadata").get("metadata").get("assessmentInstanceGuid").textValue(),
                INSTANCE_GUID);
        assertEquals(jsonNode.get("upload").get("uploadId").textValue(), UPLOAD_ID);

        // We can skip deserialization. We never read this from anywhere.
    }
}
