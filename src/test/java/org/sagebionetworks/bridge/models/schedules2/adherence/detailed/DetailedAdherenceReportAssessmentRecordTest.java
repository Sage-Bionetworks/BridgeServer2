package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;


import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AssessmentCompletionState.COMPLETED;
import static org.testng.Assert.assertEquals;

public class DetailedAdherenceReportAssessmentRecordTest {
    
    private final DateTime TIMESTAMP_WITH_OFFSET = TIMESTAMP.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE));
    
    @Test
    public void canSerialize() {
        DetailedAdherenceReportAssessmentRecord record = new DetailedAdherenceReportAssessmentRecord();
        record.setAssessmentName("assessment-name");
        record.setAssessmentId("assessment-id");
        record.setAssessmentGuid("assessment-guid");
        record.setAssessmentInstanceGuid("assessment-instance-guid");
        record.setAssessmentStatus(COMPLETED);
        record.setAssessmentStart(TIMESTAMP_WITH_OFFSET);
        record.setAssessmentCompleted(TIMESTAMP_WITH_OFFSET);
        record.setAssessmentUploadedOn(TIMESTAMP_WITH_OFFSET);
        record.setSortPriority(1);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(record);
        assertEquals(node.size(), 9);
        assertEquals(node.get("assessmentName").textValue(), "assessment-name");
        assertEquals(node.get("assessmentId").textValue(), "assessment-id");
        assertEquals(node.get("assessmentGuid").textValue(), "assessment-guid");
        assertEquals(node.get("assessmentInstanceGuid").textValue(), "assessment-instance-guid");
        assertEquals(node.get("assessmentStatus").textValue(), "completed");
        assertEquals(node.get("assessmentStart").textValue(), TIMESTAMP_WITH_OFFSET.toString());
        assertEquals(node.get("assessmentCompleted").textValue(), TIMESTAMP_WITH_OFFSET.toString());
        assertEquals(node.get("assessmentUploadedOn").textValue(), TIMESTAMP_WITH_OFFSET.toString());
        assertEquals(node.get("type").textValue(), "DetailedAdherenceReportAssessmentRecord");
    }
}
