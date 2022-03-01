package org.sagebionetworks.bridge.models.schedules2.adherence.participantschedule;

import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.TIMEZONE_MSK;
import static org.sagebionetworks.bridge.models.schedules2.participantschedules.ParticipantScheduleGenerator.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.participantschedules.ParticipantSchedule;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class ParticipantScheduleGeneratorTest {

    @Test
    public void test() throws Exception {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(TIMESTAMP)
                .build();
        
        AdherenceState state = new AdherenceState.Builder()
                .withClientTimeZone("America/Los_Angeles")
                // MSK does not change the dates, which are in user’s tz
                .withNow(TIMESTAMP.withZone(TIMEZONE_MSK)) 
                .withEvents(ImmutableList.of(e1))
                .build();
        
        ParticipantSchedule retValue = INSTANCE.generate(state, timeline);
        
        assertEquals(retValue.getCreatedOn(), TIMESTAMP.withZone(DateTimeZone.forID("America/Los_Angeles")));
        assertEquals(retValue.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(retValue.getDateRange().getStartDate().toString(), "2015-02-02");
        assertEquals(retValue.getDateRange().getEndDate().toString(), "2015-03-16");
        
        // These values are all copied over from the timeline, so they don't need to be 
        // examined very closely. They are all tested in other tests.
        assertEquals(retValue.getSessions().size(), 1);
        assertEquals(retValue.getStudyBursts().size(), 1);
        assertEquals(retValue.getAssessments().size(), 2);

        // But the schedule is different
        assertEquals(retValue.getSchedule().size(), 7);
        assertScheduledSession(retValue.getSchedule().get(0), "NpLGwpRYGr3cYjJvp945zQ", LocalDate.parse("2015-02-02"),
                LocalDate.parse("2015-02-02"), "MDoRIcMJpAZ3Xqy_uZAbnw", "AzGvv4ph-7Xzi9VRIrFyWw");
        assertScheduledSession(retValue.getSchedule().get(1), "0dVupumHdJENCi5rzjA1sQ", LocalDate.parse("2015-02-09"),
                LocalDate.parse("2015-02-09"), "mZTon_L0lXXErPKme-Ojhg", "okkx1iuHoh6MV8I8I9vYYQ");
        assertScheduledSession(retValue.getSchedule().get(2), "JzKooqt2k1rdn7A1E7j1Hg", LocalDate.parse("2015-02-16"),
                LocalDate.parse("2015-02-16"), "Pp55NkTwC1MWmAhdZWLD7A", "B2hnjJKGkFQ83rLCzkPDmA");
        assertScheduledSession(retValue.getSchedule().get(3), "8arBYlLtL93TdSjhVYDjfg", LocalDate.parse("2015-02-23"),
                LocalDate.parse("2015-02-23"), "4_qpTNQfPsIwVK9m0ok7YQ", "h-0BJQhWuU34QODRfLjRlg");
        assertScheduledSession(retValue.getSchedule().get(4), "qUvQ7R3FYbl2j-pEZ42ESQ", LocalDate.parse("2015-03-02"),
                LocalDate.parse("2015-03-02"), "y0deGrdsyat-i3CHxWzzkg", "9HaDixbXUid_O4Dc5m7Zog");
        assertScheduledSession(retValue.getSchedule().get(5), "XuyuOyJF5zs5s4cdqQISCg", LocalDate.parse("2015-03-09"),
                LocalDate.parse("2015-03-09"), "_IHC7vBzvy62AAOZDXqe6A", "MmMA1dT66qzTSnugAY-reQ");
        assertScheduledSession(retValue.getSchedule().get(6), "ym78tZHtMLaEfvx4-f9OGg", LocalDate.parse("2015-03-16"),
                LocalDate.parse("2015-03-16"), "xkucPnJH3lz_BAJ5X1DaLw", "mBwW2oM-rXEcNO7LIVi5PQ");
    }
    
    @Test
    public void emptyScheduleWorks() throws Exception {
        AdherenceState state = new AdherenceState.Builder()
                .withClientTimeZone(DateTimeZone.getDefault().getID())
                .withNow(TIMESTAMP).build();
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(new Schedule2());
        
        ParticipantSchedule retValue = INSTANCE.generate(state, timeline);
        
        assertEquals(retValue.getCreatedOn(), TIMESTAMP.withZone(DateTimeZone.getDefault()));
        assertEquals(retValue.getClientTimeZone(), DateTimeZone.getDefault().getID());
        assertNull(retValue.getDateRange());
        assertTrue(retValue.getSchedule().isEmpty());
        assertTrue(retValue.getSessions().isEmpty());
        assertTrue(retValue.getAssessments().isEmpty());
        assertTrue(retValue.getStudyBursts().isEmpty());
    }
    
    private void assertScheduledSession(ScheduledSession session, String instanceGuid, LocalDate startDate,
            LocalDate endDate, String asmtInstanceGuid1, String asmtInstanceGuid2) {
        assertEquals(session.getInstanceGuid(), instanceGuid);
        assertEquals(session.getRefGuid(), SESSION_GUID_1);
        assertEquals(session.getStartDate(), startDate);
        assertEquals(session.getEndDate(), endDate);
        assertEquals(session.getAssessments().size(), 2);
        assertNull(session.getTimeWindow().getGuid());
        assertNull(session.getStartEventId());
        assertNull(session.getStartDay());
        assertNull(session.getEndDay());
        
        // assessments are always the same
        assertEquals(session.getAssessments().get(0).getRefKey(), "646f8c04646f8c04");
        assertEquals(session.getAssessments().get(0).getInstanceGuid(), asmtInstanceGuid1);
        assertEquals(session.getAssessments().get(1).getRefKey(), "75b56f2d75b56f2d");
        assertEquals(session.getAssessments().get(1).getInstanceGuid(), asmtInstanceGuid2);
    }
}
