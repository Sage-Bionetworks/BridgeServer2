package org.sagebionetworks.bridge.models.schedules2.adherence.participantschedule;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_2_GUID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.participantschedule.ParticipantScheduleGenerator.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.AssessmentInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledAssessment;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.SessionInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class ParticipantScheduleGeneratorTest {

    @Test
    public void test() throws Exception {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        // Persistent sessions do not have state, so we need to set this to false.
        schedule.getSessions().get(0).getTimeWindows().get(0).setPersistent(false);
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(TIMESTAMP)
                .build();
        
        AdherenceRecord record1 = new AdherenceRecord();
        record1.setInstanceGuid("MDoRIcMJpAZ3Xqy_uZAbnw");
        record1.setStartedOn(CREATED_ON);
        record1.setFinishedOn(MODIFIED_ON);
        record1.setClientTimeZone("America/Chicago");
        
        // This one has no time zone, and will default to the user's current time zone
        AdherenceRecord record2 = new AdherenceRecord();
        record2.setInstanceGuid("AzGvv4ph-7Xzi9VRIrFyWw");
        record2.setStartedOn(CREATED_ON);
        record2.setFinishedOn(MODIFIED_ON);
        
        AdherenceRecord record3 = new AdherenceRecord();
        record3.setInstanceGuid("mZTon_L0lXXErPKme-Ojhg");
        record3.setStartedOn(CREATED_ON);
        
        AdherenceState state = new AdherenceState.Builder()
                .withClientTimeZone("America/Los_Angeles")
                .withNow(TIMESTAMP)
                .withEvents(ImmutableList.of(e1))
                .withAdherenceRecords(ImmutableList.of(record1, record2, record3))
                .build();
        
        ParticipantSchedule retValue = INSTANCE.generate(state, timeline);
        
        assertEquals(retValue.getCreatedOn(), TIMESTAMP.withZone(DateTimeZone.forID("America/Los_Angeles")));
        assertEquals(retValue.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(retValue.getDateRange().getStartDate().toString(), "2015-02-02");
        assertEquals(retValue.getDateRange().getEndDate().toString(), "2015-03-16");
        
        assertEquals(retValue.getSchedule().size(), 7);
        ScheduledSession sess1 = retValue.getSchedule().get(0);
        assertEquals(sess1.getState(), SessionCompletionState.NOT_YET_AVAILABLE);
        assertEquals(sess1.getStartDate().toString(), "2015-02-02");
        assertEquals(sess1.getEndDate().toString(), "2015-02-02");
        
        ScheduledAssessment schAsmt1 = sess1.getAssessments().get(0);
        assertEquals(schAsmt1.getState(), COMPLETED);
        assertEquals(schAsmt1.getClientTimeZone(), "America/Chicago");
        assertEquals(schAsmt1.getFinishedOn(), MODIFIED_ON.withZone(DateTimeZone.forID("America/Chicago")));
        
        // This one defaulted to the user's tz because none was set in the adherence record
        ScheduledAssessment schAsmt2 = sess1.getAssessments().get(1);
        assertEquals(schAsmt2.getState(), COMPLETED);
        assertNull(schAsmt2.getClientTimeZone());
        assertEquals(schAsmt2.getFinishedOn(), MODIFIED_ON);
        
        ScheduledAssessment schAsmt3 = retValue.getSchedule().get(1).getAssessments().get(0);
        assertEquals(schAsmt3.getState(), STARTED);
        assertNull(schAsmt3.getClientTimeZone());
        assertNull(schAsmt3.getFinishedOn());
        
        assertEquals(retValue.getSessions().size(), 1);
        SessionInfo sessionInfo = retValue.getSessions().get(0);
        assertEquals(sessionInfo.getGuid(), SESSION_GUID_1);
        
        assertEquals(retValue.getAssessments().size(), 2);
        AssessmentInfo asmtInfo1 = retValue.getAssessments().get(0);
        assertEquals(asmtInfo1.getGuid(), ASSESSMENT_1_GUID);
        AssessmentInfo asmtInfo2 = retValue.getAssessments().get(1);
        assertEquals(asmtInfo2.getGuid(), ASSESSMENT_2_GUID);
    }
    
    @Test
    public void test_persistentHaveNoState() throws Exception {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(TIMESTAMP)
                .build();
        
        AdherenceRecord record1 = new AdherenceRecord();
        record1.setInstanceGuid("MDoRIcMJpAZ3Xqy_uZAbnw");
        record1.setStartedOn(CREATED_ON);
        record1.setFinishedOn(MODIFIED_ON);
        record1.setClientTimeZone("America/Chicago");
        
        // This one has no time zone, and will default to the user's current time zone
        AdherenceRecord record2 = new AdherenceRecord();
        record2.setInstanceGuid("AzGvv4ph-7Xzi9VRIrFyWw");
        record2.setStartedOn(CREATED_ON);
        record2.setFinishedOn(MODIFIED_ON);
        
        AdherenceRecord record3 = new AdherenceRecord();
        record3.setInstanceGuid("mZTon_L0lXXErPKme-Ojhg");
        record3.setStartedOn(CREATED_ON);
        
        AdherenceState state = new AdherenceState.Builder()
                .withClientTimeZone("America/Los_Angeles")
                .withNow(TIMESTAMP)
                .withEvents(ImmutableList.of(e1))
                .withAdherenceRecords(ImmutableList.of(record1, record2, record3))
                .build();
        
        ParticipantSchedule retValue = INSTANCE.generate(state, timeline);
        
        ScheduledSession sess1 = retValue.getSchedule().get(0);
        assertNull(sess1.getState());
        
        ScheduledAssessment schAsmt1 = sess1.getAssessments().get(0);
        assertNull(schAsmt1.getState());
        
        ScheduledAssessment schAsmt2 = sess1.getAssessments().get(1);
        assertNull(schAsmt2.getState());
        
        ScheduledAssessment schAsmt3 = retValue.getSchedule().get(1).getAssessments().get(0);
        assertNull(schAsmt3.getState());
    }
}
