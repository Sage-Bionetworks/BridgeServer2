package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;


public class DetailedAdherenceReportSessionRecordTest {
    
    @Test
    public void canSerialize() {
        DetailedAdherenceReportAssessmentRecord assessmentRecord = new DetailedAdherenceReportAssessmentRecord();
        assessmentRecord.setAssessmentInstanceGuid("assessment-instance-guid");
        
        Map<String, DetailedAdherenceReportAssessmentRecord> assessmentRecords = new HashMap<>();
        assessmentRecords.put("assessment-instance-guid", assessmentRecord);
        
        DetailedAdherenceReportSessionRecord record = new DetailedAdherenceReportSessionRecord();
        record.setBurstName("burst-name");
        record.setBurstId("burst-id");
        record.setSessionName("session-name");
        record.setSessionGuid("session-guid");
        record.setSessionInstanceGuid("session-instance-guid");
        record.setSessionStatus(SessionCompletionState.COMPLETED);
        record.setSessionStart(TIMESTAMP);
        record.setSessionCompleted(TIMESTAMP);
        record.setSessionExpiration(TIMESTAMP);
        record.setAssessmentRecords(assessmentRecords);
        record.setSortPriority(1);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(record);
        assertEquals(node.size(), 11);
        assertEquals(node.get("burstName").textValue(), "burst-name");
        assertEquals(node.get("burstId").textValue(), "burst-id");
        assertEquals(node.get("sessionName").textValue(), "session-name");
        assertEquals(node.get("sessionGuid").textValue(), "session-guid");
        assertEquals(node.get("sessionInstanceGuid").textValue(), "session-instance-guid");
        assertEquals(node.get("sessionStatus").textValue(), "completed");
        assertEquals(node.get("sessionStart").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("sessionCompleted").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("sessionExpiration").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("type").textValue(), "DetailedAdherenceReportSessionRecord");
        
        assertEquals(node.get("assessmentRecords").get(0).get("assessmentInstanceGuid").textValue(),
                "assessment-instance-guid");
        assertEquals(node.get("assessmentRecords").get(0).get("type").textValue(),
                "DetailedAdherenceReportAssessmentRecord");
    }
    
    @Test
    public void assessmentRecordGetters() {
        DetailedAdherenceReportAssessmentRecord assessmentRecord = new DetailedAdherenceReportAssessmentRecord();
        assessmentRecord.setAssessmentInstanceGuid("assessment-instance-guid");
        
        Map<String, DetailedAdherenceReportAssessmentRecord> assessmentRecords = new HashMap<>();
        assessmentRecords.put("assessment-instance-guid", assessmentRecord);
        
        DetailedAdherenceReportSessionRecord record = new DetailedAdherenceReportSessionRecord();
        record.setAssessmentRecords(assessmentRecords);
        
        assertEquals(record.getAssessmentRecords(), ImmutableList.of(assessmentRecord));
        assertEquals(record.getAssessmentRecordMap(), ImmutableMap.of("assessment-instance-guid", assessmentRecord));
    }
    
    @Test
    public void getAssessmentRecords_sortBySortPriority() {
        DetailedAdherenceReportAssessmentRecord rec1 = createAssessmentRecord(3, 0, "guid1");
        DetailedAdherenceReportAssessmentRecord rec2 = createAssessmentRecord(2, 0, "guid2");
        DetailedAdherenceReportAssessmentRecord rec3 = createAssessmentRecord(1, 0, "guid3");
        
        DetailedAdherenceReportSessionRecord record = createSessionRecord(rec1, rec2, rec3);
        assertRecordSortOrder(record, rec3, rec2, rec1);
    }
    
    @Test
    public void getAssessmentRecords_sortByStartDate() {
        DetailedAdherenceReportAssessmentRecord rec1 = createAssessmentRecord(1, 2, "guid1");
        DetailedAdherenceReportAssessmentRecord rec2 = createAssessmentRecord(1, 1, "guid2");
        DetailedAdherenceReportAssessmentRecord rec3 = createAssessmentRecord(1, 3, "guid3");
        
        DetailedAdherenceReportSessionRecord record = createSessionRecord(rec1, rec2, rec3);
        assertRecordSortOrder(record, rec2, rec1, rec3);
    }
    
    @Test
    public void getAssessmentRecords_sortByStartDateNullsLast() {
        DetailedAdherenceReportAssessmentRecord rec1 = createAssessmentRecord(1, 2, "guid1");
        DetailedAdherenceReportAssessmentRecord rec2 = createAssessmentRecord(1, null, "guid2");
        DetailedAdherenceReportAssessmentRecord rec3 = createAssessmentRecord(1, 3, "guid3");
        
        DetailedAdherenceReportSessionRecord record = createSessionRecord(rec1, rec2, rec3);
        assertRecordSortOrder(record, rec1, rec3, rec2);
    }
    
    @Test
    public void getAssessmentRecords_sortByInstanceGuid() {
        DetailedAdherenceReportAssessmentRecord rec1 = createAssessmentRecord(1, 0, "guidA");
        DetailedAdherenceReportAssessmentRecord rec2 = createAssessmentRecord(1, 0, "guidC");
        DetailedAdherenceReportAssessmentRecord rec3 = createAssessmentRecord(1, 0, "guidB");
        
        DetailedAdherenceReportSessionRecord record = createSessionRecord(rec1, rec2, rec3);
        assertRecordSortOrder(record, rec1, rec3, rec2);
    }
    
    private void assertRecordSortOrder(DetailedAdherenceReportSessionRecord record,
                                       DetailedAdherenceReportAssessmentRecord rec1,
                                       DetailedAdherenceReportAssessmentRecord rec2,
                                       DetailedAdherenceReportAssessmentRecord rec3) {
        List<DetailedAdherenceReportAssessmentRecord> records = record.getAssessmentRecords();
        assertEquals(records.get(0), rec1);
        assertEquals(records.get(1), rec2);
        assertEquals(records.get(2), rec3);
    }
    
    private DetailedAdherenceReportAssessmentRecord createAssessmentRecord(int sortPriority, Integer hours, String guid) {
        DetailedAdherenceReportAssessmentRecord record = new DetailedAdherenceReportAssessmentRecord();
        record.setSortPriority(sortPriority);
        if (hours != null) {
            record.setAssessmentStart(TIMESTAMP.plusHours(hours));
        }
        record.setAssessmentInstanceGuid(guid);
        return record;
    }
    
    private DetailedAdherenceReportSessionRecord createSessionRecord(DetailedAdherenceReportAssessmentRecord rec1,
                                                                     DetailedAdherenceReportAssessmentRecord rec2,
                                                                     DetailedAdherenceReportAssessmentRecord rec3) {
        DetailedAdherenceReportSessionRecord record = new DetailedAdherenceReportSessionRecord();
        record.setAssessmentRecords(ImmutableMap.of(rec1.getAssessmentInstanceGuid(), rec1,
                rec2.getAssessmentInstanceGuid(), rec2, rec3.getAssessmentInstanceGuid(), rec3));
        return record;
    }
}
