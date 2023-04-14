package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DetailedAdherenceReportTest {
    
    private final DateTime TIMESTAMP_WITH_OFFSET = TIMESTAMP.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE));
    
    @Test
    public void canSerialize() {
        DetailedAdherenceReportSessionRecord sessionRecord = new DetailedAdherenceReportSessionRecord();
        sessionRecord.setSessionInstanceGuid("session-instance-guid");
        
        Map<String, DetailedAdherenceReportSessionRecord> sessionRecords = new HashMap<>();
        sessionRecords.put("session-instance-guid", sessionRecord);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        
        AccountRef participant = new AccountRef(account);
        
        DetailedAdherenceReport record = new DetailedAdherenceReport();
        record.setParticipant(participant);
        record.setTestAccount(true);
        record.setClientTimeZone("client-time-zone");
        record.setJoinedDate(TIMESTAMP_WITH_OFFSET);
        record.setSessionRecords(sessionRecords);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(record);
        
        assertEquals(node.size(), 6);
        assertEquals(node.get("participant").get("type").textValue(), "AccountRef");
        assertEquals(node.get("participant").get("identifier").textValue(), TEST_USER_ID);
        assertTrue(node.get("testAccount").booleanValue());
        assertEquals(node.get("clientTimeZone").textValue(), "client-time-zone");
        assertEquals(node.get("joinedDate").textValue(), TIMESTAMP_WITH_OFFSET.toString());
        assertEquals(node.get("type").textValue(), "DetailedAdherenceReport");
        
        assertEquals(node.get("sessionRecords").get(0).get("sessionInstanceGuid").textValue(),
                "session-instance-guid");
        assertEquals(node.get("sessionRecords").get(0).get("type").textValue(),
                "DetailedAdherenceReportSessionRecord");
    }
    
    @Test
    public void sessionRecordGetters() {
        DetailedAdherenceReportSessionRecord sessionRecord = new DetailedAdherenceReportSessionRecord();
        sessionRecord.setSessionInstanceGuid("session-instance-guid");
        
        Map<String, DetailedAdherenceReportSessionRecord> sessionRecords = new HashMap<>();
        sessionRecords.put("session-instance-guid", sessionRecord);
        
        DetailedAdherenceReport record = new DetailedAdherenceReport();
        record.setSessionRecords(sessionRecords);
        
        assertEquals(record.getSessionRecords(), ImmutableList.of(sessionRecord));
        assertEquals(record.getSessionRecordMap(), ImmutableMap.of("session-instance-guid", sessionRecord));
    }
    
    @Test
    public void getAssessmentRecords_sortBySortPriority() {
        DetailedAdherenceReportSessionRecord rec1 = createSessionRecord(3, 0, "guid1");
        DetailedAdherenceReportSessionRecord rec2 = createSessionRecord(2, 0, "guid2");
        DetailedAdherenceReportSessionRecord rec3 = createSessionRecord(1, 0, "guid3");
        
        DetailedAdherenceReport report = createReport(rec1, rec2, rec3);
        assertRecordSortOrder(report, rec3, rec2, rec1);
    }
    
    @Test
    public void getAssessmentRecords_sortByStartDate() {
        DetailedAdherenceReportSessionRecord rec1 = createSessionRecord(1, 2, "guid1");
        DetailedAdherenceReportSessionRecord rec2 = createSessionRecord(1, 1, "guid2");
        DetailedAdherenceReportSessionRecord rec3 = createSessionRecord(1, 3, "guid3");
        
        DetailedAdherenceReport report = createReport(rec1, rec2, rec3);
        assertRecordSortOrder(report, rec2, rec1, rec3);
    }
    
    @Test
    public void getAssessmentRecords_sortByStartDateNullsLast() {
        DetailedAdherenceReportSessionRecord rec1 = createSessionRecord(1, 2, "guid1");
        DetailedAdherenceReportSessionRecord rec2 = createSessionRecord(1, null, "guid2");
        DetailedAdherenceReportSessionRecord rec3 = createSessionRecord(1, 3, "guid3");
        
        DetailedAdherenceReport report = createReport(rec1, rec2, rec3);
        assertRecordSortOrder(report, rec1, rec3, rec2);
    }
    
    @Test
    public void getAssessmentRecords_sortByInstanceGuid() {
        DetailedAdherenceReportSessionRecord rec1 = createSessionRecord(1, 0, "guidA");
        DetailedAdherenceReportSessionRecord rec2 = createSessionRecord(1, 0, "guidC");
        DetailedAdherenceReportSessionRecord rec3 = createSessionRecord(1, 0, "guidB");
        
        DetailedAdherenceReport report = createReport(rec1, rec2, rec3);
        assertRecordSortOrder(report, rec1, rec3, rec2);
    }
    
    private void assertRecordSortOrder(DetailedAdherenceReport record,
                                       DetailedAdherenceReportSessionRecord rec1,
                                       DetailedAdherenceReportSessionRecord rec2,
                                       DetailedAdherenceReportSessionRecord rec3) {
        List<DetailedAdherenceReportSessionRecord> records = record.getSessionRecords();
        assertEquals(records.get(0), rec1);
        assertEquals(records.get(1), rec2);
        assertEquals(records.get(2), rec3);
    }
    
    private DetailedAdherenceReportSessionRecord createSessionRecord(int sortPriority, Integer hours, String guid) {
        DetailedAdherenceReportSessionRecord record = new DetailedAdherenceReportSessionRecord();
        record.setSortPriority(sortPriority);
        if (hours != null) {
            record.setSessionStart(TIMESTAMP.plusHours(hours));
        }
        record.setSessionInstanceGuid(guid);
        return record;
    }
    
    private DetailedAdherenceReport createReport(DetailedAdherenceReportSessionRecord rec1,
                                                 DetailedAdherenceReportSessionRecord rec2,
                                                 DetailedAdherenceReportSessionRecord rec3) {
        DetailedAdherenceReport report = new DetailedAdherenceReport();
        report.setSessionRecords(ImmutableMap.of(rec1.getSessionInstanceGuid(), rec1,
                rec2.getSessionInstanceGuid(), rec2, rec3.getSessionInstanceGuid(), rec3));
        return report;
    }
}
