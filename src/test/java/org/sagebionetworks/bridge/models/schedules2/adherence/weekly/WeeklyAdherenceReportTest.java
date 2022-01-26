package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class WeeklyAdherenceReportTest {
    @Test
    public void canSerialize() {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        
        NextActivity nextActivity = NextActivity.create(new EventStreamDay());
        
        WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow();
        row.setLabel("rowLabel");
        row.setSearchableLabel(":rowLabel:");
        row.setSessionGuid("sessionGuid");
        row.setSessionSymbol("sessionSymbol");
        row.setSessionName("sessionName");
        row.setStudyBurstId("studyBurstId");
        row.setStudyBurstNum(2);
        row.setWeek(4);
        
        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        report.setAppId(TEST_APP_ID);
        report.setStudyId(TEST_STUDY_ID);
        report.setUserId(TEST_USER_ID);
        report.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        report.setCreatedOn(MODIFIED_ON);
        report.setLabels(ImmutableSet.of("label1", "label2"));
        report.setParticipant(new AccountRef(account, "study1"));
        report.setTestAccount(true);
        report.setWeeklyAdherencePercent(79);
        report.setRows(ImmutableList.of(row));
        report.setByDayEntries(ImmutableMap.of(
                new Integer(6), ImmutableList.of(new EventStreamDay())));
        report.setNextActivity(nextActivity);

        // It's there, it works, it's persisted, but it's not part of JSON output
        assertTrue(report.isTestAccount());
        
        // These are not in the JSON
        assertEquals(report.getAppId(), TEST_APP_ID);
        assertEquals(report.getStudyId(), TEST_STUDY_ID);
        assertEquals(report.getUserId(), TEST_USER_ID);
        assertEquals(report.getLabels(), ImmutableSet.of("label1", "label2"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        
        assertEquals(node.size(), 9);
        assertNull(node.get("appId"));
        assertNull(node.get("studyId"));
        assertNull(node.get("userId"));
        assertEquals(node.get("clientTimeZone").textValue(), TEST_CLIENT_TIME_ZONE);
        assertEquals(node.get("createdOn").textValue(), MODIFIED_ON.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)).toString());
        assertEquals(node.get("weeklyAdherencePercent").intValue(), 79);
        assertEquals(node.get("participant").get("identifier").textValue(), TEST_USER_ID);
        assertTrue(node.get("testAccount").booleanValue());
        assertEquals(node.get("nextActivity").get("type").textValue(), "NextActivity");
        assertEquals(node.get("byDayEntries").get("6").get(0).get("type").textValue(), "EventStreamDay");
        assertEquals(node.get("type").textValue(), "WeeklyAdherenceReport");

        assertEquals(node.get("rows").size(), 1);
        JsonNode rowNode = node.get("rows").get(0);
        assertEquals(rowNode.get("label").textValue(), "rowLabel");
        assertEquals(rowNode.get("searchableLabel").textValue(), ":rowLabel:");
        assertEquals(rowNode.get("sessionGuid").textValue(), "sessionGuid");
        assertEquals(rowNode.get("sessionSymbol").textValue(), "sessionSymbol");
        assertEquals(rowNode.get("sessionName").textValue(), "sessionName");
        assertEquals(rowNode.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(rowNode.get("studyBurstNum").intValue(), 2);
        assertEquals(rowNode.get("week").intValue(), 4);
    }
    
    @Test
    public void clientTimeZoneUsed() {
        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        report.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        report.setCreatedOn(MODIFIED_ON);
        assertEquals(report.getCreatedOn(), MODIFIED_ON.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        
        report.setClientTimeZone(null);
        assertEquals(report.getCreatedOn(), MODIFIED_ON);
    }
    
    @Test
    public void nullSafe( ) {
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        assertEquals(node.size(), 4);
        assertFalse(node.get("activeOnly").booleanValue());
        assertEquals(node.get("adherencePercent").intValue(), 100);
        assertEquals(node.get("streams").size(), 0);
        assertEquals(node.get("type").textValue(), "EventStreamAdherenceReport");
    }
}
