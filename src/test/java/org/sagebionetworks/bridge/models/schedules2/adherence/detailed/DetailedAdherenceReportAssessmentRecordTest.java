package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;


import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;

public class DetailedAdherenceReportAssessmentRecordTest {
    
    @Test
    public void canSerialize() {
        DetailedAdherenceReportAssessmentRecord record = new DetailedAdherenceReportAssessmentRecord();
        record.setAssessmentName("assessment-name");
        record.setAssessmentId("assessment-id");
        record.setAssessmentGuid("assessment-guid");
        record.setAssessmentInstanceGuid("assessment-instance-guid");
        record.setAssessmentStatus("assessment-status");
        record.setAssessmentStart(TIMESTAMP);
        record.setAssessmentCompleted(TIMESTAMP);
        record.setAssessmentUploadedOn(TIMESTAMP);
        record.setSortPriority(1);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(record);
        assertEquals(node.size(), 9);
        assertEquals(node.get("assessmentName").textValue(), "assessment-name");
        assertEquals(node.get("assessmentId").textValue(), "assessment-id");
        assertEquals(node.get("assessmentGuid").textValue(), "assessment-guid");
        assertEquals(node.get("assessmentInstanceGuid").textValue(), "assessment-instance-guid");
        assertEquals(node.get("assessmentStatus").textValue(), "assessment-status");
        assertEquals(node.get("assessmentStart").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("assessmentCompleted").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("assessmentUploadedOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("type").textValue(), "DetailedAdherenceReportAssessmentRecord");
    }
}
