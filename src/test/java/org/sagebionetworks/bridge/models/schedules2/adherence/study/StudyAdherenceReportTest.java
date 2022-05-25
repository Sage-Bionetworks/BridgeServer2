package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.NextActivity;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class StudyAdherenceReportTest {
    
    @Test
    public void canSerialize() throws Exception {
        Account account = Account.create();
        account.setFirstName("firstName");
        account.setEmail(EMAIL);
        account.setId(TEST_USER_ID);
        
        StudyReportWeek week = new StudyReportWeek();
        
        EventStreamDay day = new EventStreamDay();
        day.setLabel("label");
        NextActivity nextActivity = NextActivity.create(day);

        StudyAdherenceReport report = new StudyAdherenceReport();
        report.setParticipant(new AccountRef(account, TEST_STUDY_ID));
        report.setTestAccount(true);
        report.setClientTimeZone("America/Chicago");
        report.setCreatedOn(CREATED_ON);
        report.setAdherencePercent(56);
        report.setProgression(ParticipantStudyProgress.IN_PROGRESS);
        report.setUnsetEventIds(ImmutableSet.of("event1", "event2"));
        report.setDateRange(new DateRange(LocalDate.parse("2022-02-02"), LocalDate.parse("2022-02-04")));
        report.setWeeks(ImmutableList.of(week));
        report.setEventTimestamps(ImmutableMap.of("event3", MODIFIED_ON));
        report.setUnscheduledSessions(ImmutableSet.of("session1", "session2"));
        report.setWeekReport(week);
        report.setNextActivity(nextActivity);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        
        assertEquals(node.get("participant").get("identifier").textValue(), "userId");
        assertEquals(node.get("participant").get("firstName").textValue(), "firstName");
        assertEquals(node.get("participant").get("email").textValue(), "email@email.com");
        assertTrue(node.get("testAccount").booleanValue());
        assertEquals(node.get("clientTimeZone").textValue(), "America/Chicago");
        assertEquals(node.get("createdOn").textValue(),
                CREATED_ON.withZone(DateTimeZone.forID("America/Chicago")).toString());
        assertEquals(node.get("adherencePercent").intValue(), 56);
        assertEquals(node.get("progression").textValue(), "in_progress");
        assertEquals(node.get("dateRange").get("startDate").textValue(), "2022-02-02");
        assertEquals(node.get("dateRange").get("endDate").textValue(), "2022-02-04");
        assertNull(node.get("currentWeek"));
        assertEquals(node.get("type").textValue(), "StudyAdherenceReport");
        
        ArrayNode weeksArray = (ArrayNode)node.get("weeks");
        assertEquals(weeksArray.get(0).get("weekInStudy").intValue(), 0);
        assertEquals(weeksArray.get(0).get("byDayEntries").size(), 7);
        assertEquals(weeksArray.get(0).get("type").textValue(), "StudyReportWeek");
        
        assertEquals(node.get("unsetEventIds").get(0).textValue(), "event1");
        assertEquals(node.get("unsetEventIds").get(1).textValue(), "event2");
        
        assertEquals(node.get("unscheduledSessions").get(0).textValue(), "session1");
        assertEquals(node.get("unscheduledSessions").get(1).textValue(), "session2");
        
        assertEquals(node.get("eventTimestamps").get("event3").textValue(), MODIFIED_ON.toString());
        
        assertEquals(node.get("type").textValue(), "StudyAdherenceReport");
    }
    
    @Test
    public void nullsWork() {
        StudyAdherenceReport report = new StudyAdherenceReport();
        JsonNode node = BridgeObjectMapper.get().valueToTree(report);
        
        assertEquals(node.size(), 2);
        assertFalse(node.get("testAccount").booleanValue());
        assertEquals(node.get("type").textValue(), "StudyAdherenceReport");
    }
}
