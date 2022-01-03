package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class AbstractAdherenceReportGeneratorTest {
    
    static final DateTimeZone CLIENT_ZONE = DateTimeZone.forID(TEST_CLIENT_TIME_ZONE);
    
    @Test
    public void test() throws Exception {
        AccountRef ref = new AccountRef(Account.create());
        
        TimelineMetadata tm1 = new TimelineMetadata();
        tm1.setSessionInstanceGuid("sessionIntanceGuid1");
        TimelineMetadata tm2 = new TimelineMetadata();
        tm2.setSessionInstanceGuid("sessionIntanceGuid2");
        List<TimelineMetadata> metadata = ImmutableList.of(tm1, tm2);
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(CREATED_ON).build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(MODIFIED_ON).build();
        List<StudyActivityEvent> events = ImmutableList.of(e1, e2);
        
        AdherenceRecord ar1 = new AdherenceRecord();
        ar1.setInstanceGuid("instanceGuid1");
        AdherenceRecord ar2 = new AdherenceRecord();
        ar2.setInstanceGuid("instanceGuid2");
        List<AdherenceRecord> adherenceRecords = ImmutableList.of(ar1, ar2);
        
        EventStreamAdherenceReportGenerator.Builder builder = new EventStreamAdherenceReportGenerator.Builder();
        builder.withAppId(TEST_APP_ID);
        builder.withStudyId(TEST_STUDY_ID);
        builder.withUserId(TEST_USER_ID);
        builder.withCreatedOn(CREATED_ON);
        builder.withMetadata(metadata);
        builder.withEvents(events);
        builder.withAdherenceRecords(adherenceRecords);
        builder.withNow(MODIFIED_ON.plusDays(2));
        builder.withShowActive(true);
        builder.withClientTimeZone(TEST_CLIENT_TIME_ZONE);
        builder.withAccount(ref);
        
        EventStreamAdherenceReportGenerator gen = builder.build();
        
        assertEquals(gen.eventTimestampByEventId.get("custom:event1"), CREATED_ON.withZone(CLIENT_ZONE));
        assertEquals(gen.eventTimestampByEventId.get("custom:event2"), MODIFIED_ON.withZone(CLIENT_ZONE));
        
        assertEquals(gen.adherenceByInstanceGuid.get("instanceGuid1"), ar1);
        assertEquals(gen.adherenceByInstanceGuid.get("instanceGuid2"), ar2);
        
        // These are 3 because "now" is two days in the future, so it's on day 3.
        assertEquals(gen.daysSinceEventByEventId.get("custom:event1"), Integer.valueOf(3));
        assertEquals(gen.daysSinceEventByEventId.get("custom:event2"), Integer.valueOf(3));
        
        assertTrue(gen.showActive);
        assertEquals(gen.now, MODIFIED_ON.plusDays(2).withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(gen.clientTimeZone, TEST_CLIENT_TIME_ZONE);
        assertSame(gen.metadata, metadata);
        assertSame(gen.adherenceRecords, adherenceRecords);
        assertSame(gen.events, events);
        assertSame(gen.account, ref);
    }
    
    @Test
    public void timeZoneFromTimeZoneRegionString() {
        EventStreamAdherenceReportGenerator.Builder builder = new EventStreamAdherenceReportGenerator.Builder();
        builder.withAppId(TEST_APP_ID);
        builder.withStudyId(TEST_STUDY_ID);
        builder.withUserId(TEST_USER_ID);
        builder.withCreatedOn(CREATED_ON);builder.withClientTimeZone(TEST_CLIENT_TIME_ZONE);
        builder.withNow(MODIFIED_ON);
        
        EventStreamAdherenceReportGenerator gen = builder.build();
        assertEquals(gen.getTimeZone(), DateTimeZone.forID(TEST_CLIENT_TIME_ZONE));
        assertEquals(gen.now, MODIFIED_ON.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
    }
    
    @Test
    public void timeZoneFromNowValue() {
        EventStreamAdherenceReportGenerator.Builder builder = new EventStreamAdherenceReportGenerator.Builder();
        builder.withAppId(TEST_APP_ID);
        builder.withStudyId(TEST_STUDY_ID);
        builder.withUserId(TEST_USER_ID);
        builder.withCreatedOn(CREATED_ON);        builder.withNow(MODIFIED_ON);
        
        EventStreamAdherenceReportGenerator gen = builder.build();
        assertEquals(gen.getTimeZone(), DateTimeZone.UTC);
        assertEquals(gen.now, MODIFIED_ON.withZone(DateTimeZone.UTC));
    }
    
    @Test
    public void timeZoneForEventFromEvent() {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withClientTimeZone(TEST_CLIENT_TIME_ZONE)
                .withTimestamp(MODIFIED_ON).build();
        
        EventStreamAdherenceReportGenerator.Builder builder = new EventStreamAdherenceReportGenerator.Builder();
        builder.withAppId(TEST_APP_ID);
        builder.withStudyId(TEST_STUDY_ID);
        builder.withUserId(TEST_USER_ID);
        builder.withCreatedOn(CREATED_ON);        
        builder.withNow(MODIFIED_ON);
        builder.withEvents(ImmutableList.of(event));
        
        EventStreamAdherenceReportGenerator gen = builder.build();
        assertEquals(gen.eventTimestampByEventId.get("custom:event1"), MODIFIED_ON.withZone(CLIENT_ZONE));
    }

    @Test
    public void timeZoneForEventFromZone() {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(MODIFIED_ON).build();
        
        EventStreamAdherenceReportGenerator.Builder builder = new EventStreamAdherenceReportGenerator.Builder();
        builder.withAppId(TEST_APP_ID);
        builder.withStudyId(TEST_STUDY_ID);
        builder.withUserId(TEST_USER_ID);
        builder.withCreatedOn(CREATED_ON);        builder.withNow(MODIFIED_ON);
        builder.withClientTimeZone(TEST_CLIENT_TIME_ZONE);
        builder.withEvents(ImmutableList.of(event));
        
        EventStreamAdherenceReportGenerator gen = builder.build();
        assertEquals(gen.eventTimestampByEventId.get("custom:event1"), MODIFIED_ON.withZone(CLIENT_ZONE));
    }
}
