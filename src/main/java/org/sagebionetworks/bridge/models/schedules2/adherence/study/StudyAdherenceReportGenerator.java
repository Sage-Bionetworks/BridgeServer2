package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateAdherencePercentage;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateProgress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.NextActivity;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class StudyAdherenceReportGenerator {

    public static final StudyAdherenceReportGenerator INSTANCE = new StudyAdherenceReportGenerator();
    
    static final Comparator<String> STRING_COMPARATOR = Comparator.nullsLast((r1, r2) -> r1.compareToIgnoreCase(r2));

    /** Sort by label ignoring case, but study bursts come first. */
    static final Comparator<WeeklyAdherenceReportRow> ROW_COMPARATOR = (r1, r2) -> {
        int sb = STRING_COMPARATOR.compare(r1.getStudyBurstId(), r2.getStudyBurstId());
        return (sb != 0) ? sb : STRING_COMPARATOR.compare(r1.getLabel(), r2.getLabel());
    };
    
    /**
     * Find a master starting date for this participant. The studyStartEventId is the canonical 
     * start of the study, but if that's not present for the study, we have identified the earliest
     * activity’s startEventId, and we’ll use that timestamp. This should reduce the appearance of 
     * negative weeks, though if the studyStartEventId comes after another event mentioned in
     * the schedule, it can happen. 
     */
    protected LocalDate getStudyStartDate(AdherenceState state, EventStreamAdherenceReport report) {
        DateTime eventTimestamp = state.getEventTimestampById(state.getStudyStartEventId());
        if (eventTimestamp != null) {
            return eventTimestamp.toLocalDate();
        }
        DateTime timestamp = state.getEventTimestampById(report.getEarliestEventId());
        if (timestamp != null) {
            return timestamp.toLocalDate();
        }
        return null;
    }

    public StudyAdherenceReport generate(AdherenceState state) {
        
        EventStreamAdherenceReport eventReport = EventStreamAdherenceReportGenerator.INSTANCE.generate(state);
        
        LocalDate studyStartDate = getStudyStartDate(state, eventReport);

        EventStream studyStream = new EventStream();
        Set<String> unsetEventIds = new HashSet<>();
        Set<String> unscheduledSessions = new HashSet<>();
        Map<String, DateTime> eventTimestamps = new HashMap<>();
        LocalDate todayLocal = state.getNow().toLocalDate();
        
        // Remap all the event streams to one event stream — the study event stream
        for (EventStream stream : eventReport.getStreams()) {
            for (List<EventStreamDay> days : stream.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    if (oneDay.getStartDate() != null) {
                        // Map this stream into the study stream
                        Integer day = Days.daysBetween(studyStartDate, oneDay.getStartDate()).getDays();
                        studyStream.addEntry(day, oneDay);
                        
                        String eventId = oneDay.getStartEventId();
                        eventTimestamps.put(eventId, state.getEventTimestampById(eventId));
                    } else {
                        // Note for the report the events and sessions that are not applicable to this user
                        unsetEventIds.add(oneDay.getStartEventId());
                        unscheduledSessions.add(oneDay.getSessionName());
                    }
                }
            }
        }
        
        // Break this study stream down into weeks. Keys are sorted by natural order 
        Map<Integer, StudyReportWeek> weekMap = new TreeMap<>();
        StudyReportWeek currentWeek = null;
        
        for (Map.Entry<Integer, List<EventStreamDay>> entry : studyStream.getByDayEntries().entrySet()) {
            int week = entry.getKey() / 7;
            int dayOfWeek = entry.getKey() % 7;
            
            StudyReportWeek oneWeek = weekMap.get(week);
            if (oneWeek == null) {
                oneWeek = new StudyReportWeek();
                // This might be useful later to find “this week” — we’ll see
                oneWeek.setStartDate(studyStartDate.plusDays(entry.getKey()));
                oneWeek.setWeek(week+1);
                weekMap.put(week, oneWeek);
            }
            // the days have a week value that is based on their original event stream, change this to avoid confusion
            // unnecessary now that we clear this value.
            // entry.getValue().forEach(day -> day.setWeek(week+1));
            List<EventStreamDay> days = oneWeek.getByDayEntries().get(dayOfWeek);
            // days can be negative when the events do not line up between the participant and the schedule,
            // such that the days in the study can even be negative. It doesn’t make sense, but defend 
            // against it because users can break their schedules this way.
            if (days != null) {
                days.addAll(entry.getValue());
            }
        };
        
        Collection<StudyReportWeek> weeks = weekMap.values();
        
        for (StudyReportWeek oneWeek : weeks) {
            // Calculate adherence if there’s at least one scheduled thing in the week
            if (AdherenceUtils.countDays(oneWeek.getByDayEntries().values(), SessionCompletionState.SCHEDULED) > 0) {
                int adherence = AdherenceUtils.calculateAdherencePercentage(oneWeek.getByDayEntries());
                oneWeek.setAdherencePercent(adherence);
            }
            // Add labels and calculate row descriptors for the whole week.
            calculateRowsAndLabels(oneWeek);
            
            // If there’s a day in this week that is “today”, set a flag for all day entries on that day
            for (List<EventStreamDay> days : oneWeek.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    if (oneDay.getStartDate() != null && oneDay.getStartDate().isEqual(todayLocal)) {
                        currentWeek = oneWeek;
                        days.forEach(day -> day.setToday(true));
                    }
                }
            }
        }
        ParticipantStudyProgress progression = calculateProgress(state, ImmutableList.of(studyStream));
        Integer adherence = null;
        if (!studyStream.getByDayEntries().isEmpty()) {
            adherence = calculateAdherencePercentage(ImmutableList.of(studyStream));    
        }
        NextActivity nextActivity = null;
        if (currentWeek == null || !currentWeek.isActiveWeek()) {
            nextActivity = getNextActivity(weeks, state.getNow());
        }
        // Clean unnecessary fields for this report
        for (StudyReportWeek oneWeek : weeks) {
            for (List<EventStreamDay> days : oneWeek.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    oneDay.setStudyBurstId(null);
                    oneDay.setStudyBurstNum(null);
                    oneDay.setSessionName(null);
                    // oneDay.setStartEventId(null);
                    oneDay.setWeek(null);
                    oneDay.setStartDay(null);
                    for (EventStreamWindow window : oneDay.getTimeWindows()) {
                        window.setEndDay(null);
                        // timeWindowGuid is actually necessary or the window is removed
                    }
                }
            }
        }
        
        DateRange dateRange = null;
        if (eventReport.getDateRangeOfAllStreams() != null) {
            dateRange = new DateRange(studyStartDate, eventReport.getDateRangeOfAllStreams().getEndDate());
        }
        
        StudyAdherenceReport report = new StudyAdherenceReport();
        report.setDateRange(dateRange);
        report.setWeeks(weeks);
        report.setCurrentWeek(currentWeek);
        report.setNextActivity(nextActivity);
        report.setProgression(progression);
        report.setAdherencePercent(adherence);
        report.setEventTimestamps(eventTimestamps);
        report.setUnsetEventIds(unsetEventIds);
        report.setUnscheduledSessions(unscheduledSessions);
        return report;
    }

    protected void calculateRowsAndLabels(StudyReportWeek oneWeek) {
        Set<String> labels = new LinkedHashSet<>();
        Set<WeeklyAdherenceReportRow> rows = new LinkedHashSet<>();
        for (List<EventStreamDay> days : oneWeek.getByDayEntries().values()) {
            for (EventStreamDay oneDay : days) {
                // The main searches to support are:
                // <Study Burst>
                // <Study Burst ID> 1
                // <Study Burst ID> 1:Week 1
                // <Session Name>
                // <Session Name>:Week 1
                // Week 1
                String searchableLabel = (oneDay.getStudyBurstId() != null) ?
                    String.format(":%s:%s %s:Week %s:%s:", oneDay.getStudyBurstId(), oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneWeek.getWeek(), oneDay.getSessionName()) :
                    String.format(":%s:Week %s:", oneDay.getSessionName(), oneWeek.getWeek());
                String displayLabel = (oneDay.getStudyBurstId() != null) ?
                        String.format("%s %s / Week %s / %s", oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneWeek.getWeek(), oneDay.getSessionName()) :
                        String.format("%s / Week %s", oneDay.getSessionName(), oneWeek.getWeek());
                labels.add(searchableLabel);
                
                WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow(); 
                row.setLabel(displayLabel);
                row.setSearchableLabel(searchableLabel);
                row.setSessionGuid(oneDay.getSessionGuid());
                row.setStartEventId(oneDay.getStartEventId());
                row.setSessionName(oneDay.getSessionName());
                row.setSessionSymbol(oneDay.getSessionSymbol());
                row.setStudyBurstId(oneDay.getStudyBurstId());
                row.setStudyBurstNum(oneDay.getStudyBurstNum());
                row.setWeek(oneWeek.getWeek());
                rows.add(row);
            }
        }

        // Now pad the rows so they can be read straight through in any UI of the report. 
        // Sort the rows so they display in an order that makes sense
        List<WeeklyAdherenceReportRow> rowList = Lists.newArrayList(rows);
        rowList.sort(ROW_COMPARATOR);
        
        for (int i=0; i < 7; i++) {
            List<EventStreamDay> paddedDays = new ArrayList<>();
            
            for (WeeklyAdherenceReportRow row : rowList) {
                List<EventStreamDay> days = oneWeek.getByDayEntries().get(i);
                EventStreamDay oneDay = padEventStreamDay(days, row.getSessionGuid(), row.getStartEventId());
                paddedDays.add(oneDay);
            }
            oneWeek.getByDayEntries().put(i, paddedDays);
        }
        oneWeek.setSearchableLabels(labels);
        oneWeek.setRows(rowList);
    }
    
    private EventStreamDay padEventStreamDay(List<EventStreamDay> days, String sessionGuid, String eventId) {
        if (days != null) {
            // If a session is triggered twice by two different events, it can appear twice in rows.
            // If the events have nearly the same timestamp, the GUIDs and the labels would be exactly
            // the same. For this reason we carry over the event ID as this identifies two unique streams
            // and these must be tracked separately, even though they look the same in the report.
            Optional<EventStreamDay> oneDay = days.stream()
                    .filter(day -> day.getSessionGuid().equals(sessionGuid) && day.getStartEventId().equals(eventId))
                    .findFirst();
            if (oneDay.isPresent()) {
                return oneDay.get();
            }
        }
        return new EventStreamDay();
    }
    
    private NextActivity getNextActivity(Collection<StudyReportWeek> weeks, DateTime now) {
        for (StudyReportWeek oneWeek : weeks) {
            if (oneWeek.getStartDate().isAfter(now.toLocalDate())) {
                for (List<EventStreamDay> days : oneWeek.getByDayEntries().values()) {
                    for (EventStreamDay oneDay : days) {
                        if (!oneDay.getTimeWindows().isEmpty()) {
                            oneDay.setWeek(oneWeek.getWeek());
                            return NextActivity.create(oneDay);
                        }
                    }
                }
            }
        }
        return null;
    }
}
