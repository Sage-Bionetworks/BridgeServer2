package org.sagebionetworks.bridge.models.schedules2.adherence.participantschedule;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;

import java.util.Comparator;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledAssessment;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.SessionInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

public class ParticipantScheduleGenerator {
    private static final LocalDate EARLIEST_LOCAL_DATE = LocalDate.parse("1900-01-01");
    private static final LocalDate LATEST_LOCAL_DATE = LocalDate.parse("9999-12-31");
    
    private static final Comparator<ScheduledSession> SCHEDULED_SESSION_COMPARATOR = (sch1, sch2) -> {
        int order = sch1.getStartDate().compareTo(sch2.getStartDate());
        if (order == 0) {
            return sch1.getStartTime().compareTo(sch2.getStartTime());
        }
        return order;
    };

    public static final ParticipantScheduleGenerator INSTANCE = new ParticipantScheduleGenerator();

    public ParticipantSchedule generate(AdherenceState state, Timeline timeline) {
        Multimap<LocalDate, ScheduledSession> chronology = LinkedHashMultimap.create();
        LocalDate earliestDate = LATEST_LOCAL_DATE;
        LocalDate latestDate = EARLIEST_LOCAL_DATE;
        
        for (ScheduledSession schSession : timeline.getSchedule()) {
            String eventId = schSession.getStartEventId();
            DateTime eventTimestamp = state.getEventTimestampById(eventId);
            if (eventTimestamp == null) {
                continue;
            }
            Integer days = state.getDaysSinceEventById(eventId);
            LocalDate startDate = eventTimestamp.plusDays(schSession.getStartDay()).toLocalDate();
            LocalDate endDate = eventTimestamp.plusDays(schSession.getEndDay()).toLocalDate();
            AdherenceRecord record = state.getAdherenceRecordByGuid(schSession.getInstanceGuid());
            SessionCompletionState schState = AdherenceUtils.calculateSessionState(
                    record, schSession.getStartDay(), schSession.getEndDay(), days);
            
            ScheduledSession.Builder builder = schSession.toBuilder();
            builder.withStartDate(startDate);
            builder.withEndDate(endDate);
            // We do not show state for persistent sessions, because it makes no sense (it's the most recent
            // timestamp, but that's just confusing).
            if (!TRUE.equals(schSession.isPersistent())) {
                builder.withState(schState);
            }
            if (startDate.isBefore(earliestDate)) {
                earliestDate = startDate;
            }
            if (endDate.isAfter(latestDate)) {
                latestDate = endDate;
            }
            for (ScheduledAssessment schAssessment : schSession.getAssessments()) {
                AdherenceRecord asmtRecord = state.getAdherenceRecordByGuid(schAssessment.getInstanceGuid());    
                SessionCompletionState asmtState = AdherenceUtils.calculateSessionState(
                        asmtRecord, schSession.getStartDay(), schSession.getEndDay(), days);
                
                // Copy these values over to from the adherence record if they exist, and put the timestamp
                // in the supplied timezone if it has been persisted.
                DateTime finishedOn = null;
                String clientTimeZone = null;
                if (asmtRecord != null && asmtRecord.getFinishedOn() != null) {
                    finishedOn = asmtRecord.getFinishedOn();
                }
                if (asmtRecord != null && asmtRecord.getClientTimeZone() != null) {
                    clientTimeZone = asmtRecord.getClientTimeZone();
                }
                if (finishedOn != null && clientTimeZone != null) {
                    DateTimeZone zone = DateTimeZone.forID(clientTimeZone);
                    finishedOn = finishedOn.withZone(zone);
                }
                ScheduledAssessment.Builder asmtBuilder = new ScheduledAssessment.Builder()
                        .withRefKey(schAssessment.getRefKey())
                        .withInstanceGuid(schAssessment.getInstanceGuid())
                        .withFinishedOn(finishedOn)
                        .withClientTimeZone(clientTimeZone);
                // We do not show state for persistent sessions, because it makes no sense (it's the most recent
                // timestamp, but that's just confusing).
                if (!TRUE.equals(schSession.isPersistent())) {
                    asmtBuilder.withState(asmtState);
                }
                builder.withScheduledAssessment(asmtBuilder.build());
            }
            // null these out, not useful
            schSession.getTimeWindow().setGuid(null);
            builder.withStartEventId(null);
            builder.withStartDay(null);
            builder.withEndDay(null);
            chronology.put(startDate, builder.build());
        }

        // chronology.size() is the total number of pairs, not the total number of keys (and thus correct).
        List<ScheduledSession> scheduledSessions = Lists.newArrayListWithCapacity(chronology.size());
        for (LocalDate date : chronology.keySet()) {
            scheduledSessions.addAll(chronology.get(date));
        }
        scheduledSessions.sort(SCHEDULED_SESSION_COMPARATOR);
        
        DateRange range = null;
        if (earliestDate.isBefore(latestDate)) {
            range = new DateRange(earliestDate, latestDate);
        }
        
        ParticipantSchedule schedule = new ParticipantSchedule();
        schedule.setCreatedOn(state.getNow());
        schedule.setClientTimeZone(state.getClientTimeZone());
        schedule.setDateRange(range);
        schedule.setSchedule(scheduledSessions);
        schedule.setSessions(timeline.getSessions().stream()
                .map(SessionInfo::createScheduleEntry).collect(toList()));
        schedule.setAssessments(timeline.getAssessments());
        schedule.setStudyBursts(timeline.getStudyBursts());
        return schedule;
    }
}
