package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class WeeklyAdherenceReportGenerator {
    
    public static final WeeklyAdherenceReportGenerator INSTANCE = new WeeklyAdherenceReportGenerator();
    
    static final Comparator<String> STRING_COMPARATOR = Comparator.nullsLast((r1, r2) -> r1.compareToIgnoreCase(r2));
    
    /** Sort by label ignoring case, but study bursts come first. */
    static final Comparator<WeeklyAdherenceReportRow> ROW_COMPARATOR = (r1, r2) -> {
        int sb = STRING_COMPARATOR.compare(r1.getStudyBurstId(), r2.getStudyBurstId());
        return (sb != 0) ? sb : STRING_COMPARATOR.compare(r1.getLabel(), r2.getLabel());
    };

    public WeeklyAdherenceReport generate(AdherenceState state) {
        
        // The service always sets showActive=false, but for tests it is useful to force this
        AdherenceState stateCopy = state.toBuilder().withShowActive(false).build();
        EventStreamAdherenceReport eventReport = EventStreamAdherenceReportGenerator.INSTANCE.generate(stateCopy);
        
        EventStream finalReport = new EventStream();

        for (EventStream report : eventReport.getStreams()) {
            Integer currentDay = state.getDaysSinceEventById(report.getStartEventId());
            if (currentDay == null || currentDay < 0) {
                // Participant has not yet begun the activities scheduled by this event
                continue;
            }
            int currentWeek = currentDay / 7;    
            int startDayOfWeek = (currentWeek*7);
            int endDayOfWeek = startDayOfWeek+6;
            
            // Object instance identity is used to prevent duplication of entries
            Set<EventStreamDay> selectedDays = new LinkedHashSet<>();
            for (Integer dayInReport : report.getByDayEntries().keySet()) {
                List<EventStreamDay> days = report.getByDayEntries().get(dayInReport);
                for (EventStreamDay oneDay : days) {
                    int startDay = oneDay.getStartDay();
                    for (EventStreamWindow window : oneDay.getTimeWindows()) {
                        int endDay = window.getEndDay();
                        // The time window of this day falls within the current week for this event stream
                        if (startDay <= endDayOfWeek && endDay >= startDayOfWeek) {
                            oneDay.setWeek(currentWeek+1);
                            selectedDays.add(oneDay);
                        }
                    }
                }
            }
            for (EventStreamDay oneDay : selectedDays) {
                int dayOfWeek = oneDay.getStartDay() - startDayOfWeek;
                // if dayOfWeek is negative, the session started prior to this week and it
                // is still active, so it should still be in the list. Currently given the visual designs
                // we have, we are positioning these sessions on day 0.
                if (dayOfWeek < 0) {
                    dayOfWeek = 0;
                }
                finalReport.addEntry(dayOfWeek, oneDay);   
            }
        }
        
        // The report is now entirely sparse, which is an issue. We're going to iterate through all of it
        // and determine the full set of rows that are present, but then we need to fill the report in.

        // Add labels. The labels are colon-separated here to facilitate string searches on the labels.
        Set<String> labels = new LinkedHashSet<>();
        Set<WeeklyAdherenceReportRow> rows = new LinkedHashSet<>();
        for (List<EventStreamDay> days : finalReport.getByDayEntries().values()) {
            for (EventStreamDay oneDay : days) {
                // The main searches to support are:
                // <Study Burst ID> 1
                // <Study Burst ID> 1:Week 1
                // <Session Name>
                // <Session Name>:Week 1
                // Week 1
                String searchableLabel = (oneDay.getStudyBurstId() != null) ?
                    String.format(":%s %s:Week %s:%s:", oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneDay.getWeek(), oneDay.getSessionName()) :
                    String.format(":%s:Week %s:", oneDay.getSessionName(), oneDay.getWeek());
                String displayLabel = (oneDay.getStudyBurstId() != null) ?
                        String.format("%s %s / Week %s / %s", oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneDay.getWeek(), oneDay.getSessionName()) :
                        String.format("%s / Week %s", oneDay.getSessionName(), oneDay.getWeek());
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
                row.setWeek(oneDay.getWeek());
                rows.add(row);
            }
        }
        
        // Now pad the report to fix the number and position of the row entries, so the rows
        // can be read straight through in any UI of the report. Sort the rows so they display
        // in an order that makes sense.
        List<WeeklyAdherenceReportRow> rowList = Lists.newArrayList(rows);
        rowList.sort(ROW_COMPARATOR);
        
        for (int i=0; i < 7; i++) { // day of week
            List<EventStreamDay> paddedDays = new ArrayList<>();
            
            for (WeeklyAdherenceReportRow row : rowList) {
                List<EventStreamDay> days = finalReport.getByDayEntries().get(i);
                EventStreamDay oneDay = padEventStreamDay(days, row.getSessionGuid(), row.getStartEventId());
                paddedDays.add(oneDay);
            }
            finalReport.getByDayEntries().put(i, paddedDays);
        }
        
        // If the report is empty, then we seek ahead and store information on the next activity
        EventStreamDay nextDay = null;
        if (rowList.isEmpty()) {
            nextDay = getNextActivity(state, finalReport, eventReport);
        }
        int percentage = AdherenceUtils.calculateAdherencePercentage(ImmutableList.of(finalReport));
        
        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        report.setByDayEntries(finalReport.getByDayEntries());
        report.setCreatedOn(state.getNow());
        report.setClientTimeZone(state.getClientTimeZone());
        report.setWeeklyAdherencePercent(percentage);
        report.setNextActivity(NextActivity.create(nextDay));
        report.setSearchableLabels(labels);
        report.setRows(rowList);
        
        // Remove this information from the day entries, as we've moved it to rows in this report.
        for (List<EventStreamDay> days : report.getByDayEntries().values()) {
            for (EventStreamDay oneDay : days) {
                oneDay.setSessionName(null);
                oneDay.setSessionSymbol(null);
                oneDay.setStudyBurstId(null);
                oneDay.setStudyBurstNum(null);
                oneDay.setWeek(null);
            }
        }
        return report;
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
    
    private EventStreamDay getNextActivity(AdherenceState state, EventStream finalReport, EventStreamAdherenceReport reports) {
        
        for (EventStream report : reports.getStreams()) {
            for (List<EventStreamDay> days :report.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    LocalDate startDate = oneDay.getStartDate();
                    // If an activity is "not applicable," then the startDate cannot be determined and
                    // it will be null...and it's not the next activity for this user, so skip it.
                    if (startDate == null || oneDay.getTimeWindows().isEmpty()) {
                        continue;
                    }
                    if (startDate.isAfter(state.getNow().toLocalDate())) {
                        oneDay.setStartDay(null);
                        oneDay.setStudyBurstId(report.getStudyBurstId());
                        oneDay.setStudyBurstNum(report.getStudyBurstNum());
                        Integer currentDay = state.getDaysSinceEventById(report.getStartEventId());
                        // currentDay cannot be null (if it were, there would have been no startDate)
                        if (currentDay >= 0) {
                            int currentWeek = currentDay / 7;
                            oneDay.setWeek(currentWeek+1);    
                        }
                        return oneDay;
                    }
                }
            }
        }
        return null;
    }
}
