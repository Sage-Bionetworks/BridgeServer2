package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;

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
        
        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        report.setAppId(TEST_APP_ID);
        report.setStudyId(TEST_STUDY_ID);
        report.setUserId(TEST_USER_ID);
        report.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        report.setCreatedOn(CREATED_ON);
        report.setLabels(ImmutableSet.of("label1", "label2"));
        report.setParticipant(new AccountRef(account, "study1"));
        report.setWeeklyAdherencePercent(79);
        report.setTimestamp(MODIFIED_ON);
        report.setByDayEntries(ImmutableMap.of(
                new Integer(6), ImmutableList.of(new EventStreamDay())));
        report.setNextActivity(nextActivity);
        
        // These are not in the JSON
        assertEquals(report.getAppId(), TEST_APP_ID);
        assertEquals(report.getStudyId(), TEST_STUDY_ID);
        assertEquals(report.getUserId(), TEST_USER_ID);
        assertEquals(report.getLabels(), ImmutableSet.of("label1", "label2"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        assertNull(node.get("appId"));
        assertNull(node.get("studyId"));
        assertNull(node.get("userId"));
        assertEquals(node.get("clientTimeZone").textValue(), TEST_CLIENT_TIME_ZONE);
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertNull(node.get("labels"));
        assertEquals(node.get("timestamp").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("weeklyAdherencePercent").intValue(), 79);
        assertEquals(node.get("type").textValue(), "WeeklyAdherenceReport");
        assertEquals(node.get("participant").get("identifier").textValue(), TEST_USER_ID);
        assertEquals(node.get("nextActivity").get("type").textValue(), "NextActivity");
        assertEquals(node.get("byDayEntries").get("6").get(0).get("type").textValue(), "EventStreamDay");
        
        report.setParticipant(new AccountRef(account, "study1"));
        report.setByDayEntries(ImmutableMap.of(new Integer(6), ImmutableList.of()));
        report.setNextActivity(nextActivity);
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
