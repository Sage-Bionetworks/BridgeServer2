package org.sagebionetworks.bridge.models.schedules2.adherence;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.DECLINED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;
import static org.testng.Assert.assertEquals;

import java.util.Collection;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class AdherenceUtilsTest {

    private static final String EVENT_TS = "2021-10-15T08:13:47.345-07:00";
    private static final DateTime STARTED_ON = TestConstants.CREATED_ON;
    private static final DateTime FINISHED_ON = TestConstants.MODIFIED_ON;

    @Test
    public void calculateSessionState_notApplicable() {
        AdherenceRecord record = createAdherenceRecord(null, null, false);
        
        assertEquals(AdherenceUtils.calculateSessionState(record, 0, 0, null), NOT_APPLICABLE);
    }

    // Not sure about this, as we won't show it was done in adherence reports, but we also
    // can't tell if it was abandoned, done outside the window entirely, etc.
    @Test
    public void calculateSessionState_notApplicableButStartedAnyway() {
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, null, false);
        
        assertEquals(AdherenceUtils.calculateSessionState(record, 0, 0, null), NOT_APPLICABLE);
    }
    
    // Not sure about this, as we won't show it was done in adherence reports, but we also
    // can't tell if it was abandoned, done outside the window entirely, etc.
    @Test
    public void calculateSessionState_notApplicableButFinishedAnyway() {
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, FINISHED_ON, false);
        
        assertEquals(AdherenceUtils.calculateSessionState(record, 0, 0, null), NOT_APPLICABLE);
    }
    
    @Test
    public void calculateSessionState_notYetAvailable() { 
        assertEquals(AdherenceUtils.calculateSessionState(null, 2, 3, 0), NOT_YET_AVAILABLE);
    }

    @Test
    public void calculateSession_expiredNoRecord() {
        assertEquals(AdherenceUtils.calculateSessionState(null, 1, 2, 3), EXPIRED);
    }
    
    @Test
    public void calculateSession_expiredEmptyRecord() {
        AdherenceRecord record = createAdherenceRecord(null, null, false);
        
        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 2, 3), EXPIRED);
    }
    
    @Test
    public void calculateSession_unstartedNoRecord() { 
        assertEquals(AdherenceUtils.calculateSessionState(null, 1, 3, 2), UNSTARTED);
    }

    @Test
    public void calculateSession_unstartedEmptyRecord() { 
        AdherenceRecord record = createAdherenceRecord(null, null, false);
        
        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 2), UNSTARTED);
    }
    
    @Test
    public void calculateSession_declined() { 
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, FINISHED_ON, true);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 2), DECLINED);
    }
    
    @Test
    public void calculateSession_declinedWithoutDates() { 
        AdherenceRecord record = createAdherenceRecord(null, null, true);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 2), DECLINED);
    }
    
    @Test
    public void calculateSession_completedAndExpired() { 
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, FINISHED_ON, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 2, 3), COMPLETED);
    }
    
    @Test
    public void calculateSession_abandoned() { 
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, null, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 2, 3), ABANDONED);
    }
    
    @Test
    public void calculateSession_expiredAfterWindowNoRecord() {
        assertEquals(AdherenceUtils.calculateSessionState(null, 1, 2, 3), EXPIRED);
    }
    
    @Test
    public void calculateSession_expiredAfterWindowEmptyRecord() {
        AdherenceRecord record = createAdherenceRecord(null, null, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 2, 3), EXPIRED);
    }
    
    @Test
    public void calculateSession_completedAndActive() { 
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, FINISHED_ON, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 3), COMPLETED);
    }

    @Test
    public void calculateSession_started() { 
        AdherenceRecord record = createAdherenceRecord(STARTED_ON, null, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 3), STARTED);
    }

    @Test
    public void calculateSession_unstartedRecordWithNoTimestamps() { 
        AdherenceRecord record = createAdherenceRecord(null, null, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 3), UNSTARTED);
    }
    
    @Test
    public void calculateSession_finishedOnlyTreatedAsComplete() { 
        AdherenceRecord record = createAdherenceRecord(null, FINISHED_ON, false);

        assertEquals(AdherenceUtils.calculateSessionState(record, 1, 3, 3), COMPLETED);
    }
    
    @Test
    public void calculateAdherencePercentage_noStreams() {
        int retValue = AdherenceUtils.calculateAdherencePercentage(ImmutableList.of());
        assertEquals(retValue, 100);
    }
    
    @Test
    public void calculateAdherencePercentage_noDays() {
        EventStream stream = new EventStream();
        int retValue = AdherenceUtils.calculateAdherencePercentage(ImmutableList.of(stream));
        assertEquals(retValue, 100);
    }
    
    @Test
    public void calculateAdherencePercentage_noWindows() {
        EventStream stream = new EventStream();
        stream.addEntry(0, new EventStreamDay());
        stream.addEntry(2, new EventStreamDay());
        stream.addEntry(4, new EventStreamDay());
        int retValue = AdherenceUtils.calculateAdherencePercentage(ImmutableList.of(stream));
        assertEquals(retValue, 100);
    }
    
    @Test(dataProvider = "eventStreams")
    public void calculateAdherencePercentage(int expectedPercent, Collection<EventStream> streams) {
        int retValue = AdherenceUtils.calculateAdherencePercentage(streams);
        assertEquals(retValue, expectedPercent);
    }
    
    @DataProvider(name = "eventStreams")
    public static Object[] eventStreams() {
        // This is admittedly a random selection of cases, I don't have anything systematic
        // in mind here.
        return new Object[][] {
            dataRow(100,createEventStream(0, NOT_YET_AVAILABLE, NOT_YET_AVAILABLE)),
            dataRow(100,createEventStream(0, COMPLETED, COMPLETED),
                        createEventStream(1, COMPLETED, COMPLETED)),
            dataRow(0,  createEventStream(0, EXPIRED, NOT_YET_AVAILABLE)),
            dataRow(100,createEventStream(0, NOT_APPLICABLE, NOT_APPLICABLE),
                        createEventStream(0, NOT_APPLICABLE, NOT_APPLICABLE)),
            dataRow(100,createEventStream(0, COMPLETED, NOT_APPLICABLE),
                        createEventStream(0, NOT_APPLICABLE, NOT_APPLICABLE)),
            dataRow(0,  createEventStream(0, ABANDONED, NOT_APPLICABLE),
                        createEventStream(0, NOT_APPLICABLE, NOT_APPLICABLE)),
            dataRow(50, createEventStream(0, COMPLETED, null),
                        createEventStream(2, EXPIRED, null)),
            dataRow(20, createEventStream(0, EXPIRED, null),
                        createEventStream(1, EXPIRED, COMPLETED),
                        createEventStream(2, UNSTARTED, UNSTARTED),
                        createEventStream(3, NOT_YET_AVAILABLE, NOT_YET_AVAILABLE)),
            dataRow(25, createEventStream(0, EXPIRED, null),
                        createEventStream(1, EXPIRED, COMPLETED),
                        createEventStream(2, UNSTARTED, null)),
            dataRow(33, createEventStream(0, COMPLETED, COMPLETED),
                        createEventStream(1, STARTED, STARTED),
                        createEventStream(2, UNSTARTED, UNSTARTED)),
            dataRow(66, createEventStream(0, COMPLETED, COMPLETED),
                        createEventStream(1, COMPLETED, COMPLETED),
                        createEventStream(2, UNSTARTED, UNSTARTED)),
            dataRow(33, createEventStream(0, COMPLETED, DECLINED),
                        createEventStream(1, COMPLETED, DECLINED),
                        createEventStream(2, STARTED, UNSTARTED)),
            dataRow(33, createEventStream(0, ABANDONED, COMPLETED),
                        createEventStream(1, COMPLETED, EXPIRED),
                        createEventStream(2, STARTED, UNSTARTED),
                        createEventStream(3, NOT_YET_AVAILABLE, null),
                        createEventStream(4, NOT_APPLICABLE, null))
        };
    }
    
    private static Object[] dataRow(int expectedPercentage, EventStream... streams) {
        return new Object[] { expectedPercentage, ImmutableList.copyOf(streams) };
    }
    
    private static EventStream createEventStream(int dayNum, SessionCompletionState state, SessionCompletionState state2) {
        EventStreamDay day = new EventStreamDay();

        EventStreamWindow window = new EventStreamWindow();
        window.setTimeWindowGuid("guid1" + dayNum);
        window.setState(state);
        day.addTimeWindow(window);
        
        if (state2 !=  null) {
            window = new EventStreamWindow();
            window.setTimeWindowGuid("guid2" + dayNum);
            window.setState(state2);
            day.addTimeWindow(window);
        }
        EventStream stream = new EventStream();
        stream.addEntry(dayNum, day);
        
        return stream;
    }
    
    private AdherenceRecord createAdherenceRecord(DateTime startedOn, DateTime finishedOn, boolean declined) {
        return createAdherenceRecord(SCHEDULE_GUID, startedOn, finishedOn, EVENT_TS, declined);
    }

    private AdherenceRecord createAdherenceRecord(String guid, DateTime startedOn, DateTime finishedOn, String eventTs,
            boolean declined) {
        AdherenceRecord sess = new AdherenceRecord();
        sess.setAppId(TEST_APP_ID);
        sess.setUserId(TEST_USER_ID);
        sess.setStudyId(TEST_STUDY_ID);
        sess.setInstanceGuid(guid);
        sess.setStartedOn(startedOn);
        sess.setFinishedOn(finishedOn);
        sess.setDeclined(TRUE.equals(declined));
        sess.setEventTimestamp(DateTime.parse(eventTs));
        return sess;
    }
}