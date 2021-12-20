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

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.TestConstants;
import org.testng.annotations.Test;

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