package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

public class WeeklyAdherenceReportGenerator {
    
    public static final WeeklyAdherenceReportGenerator INSTANCE = new WeeklyAdherenceReportGenerator();

    public WeeklyAdherenceReport generate(AdherenceState state) {
        
        // The service always sets showActive=false, but for tests it is useful to force this
        AdherenceState stateCopy = state.toBuilder().withShowActive(false).build();
        EventStreamAdherenceReport reports = EventStreamAdherenceReportGenerator.INSTANCE.generate(stateCopy);
        
        EventStream finalReport = new EventStream();

        for (EventStream report : reports.getStreams()) {
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

        // Add labels. These are also added to the labels collection so we can persist them as a model 
        // collection and search on them.
        Set<String> labels = new HashSet<>();
        for (List<EventStreamDay> days : finalReport.getByDayEntries().values()) {
            for (EventStreamDay oneDay : days) {
                String label = (oneDay.getStudyBurstId() == null) ?
                        String.format("%s / Week %s", oneDay.getSessionName(), oneDay.getWeek()) :
                        String.format("%s %s / Week %s", oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneDay.getWeek());
                labels.add(label);
                oneDay.setLabel(label);
            }
        }

        // If the report is empty, then we seek ahead and store information on the next activity
        EventStreamDay nextDay = getNextActivity(state, finalReport, reports);
        int percentage = AdherenceUtils.calculateAdherencePercentage(ImmutableList.of(finalReport));

        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        report.setByDayEntries(finalReport.getByDayEntries());
        report.setCreatedOn(state.getNow());
        report.setClientTimeZone(state.getClientTimeZone());
        report.setWeeklyAdherencePercent(percentage);
        report.setNextActivity(NextActivity.create(nextDay));
        report.setLabels(labels);
        return report;
    }
    
    private EventStreamDay getNextActivity(AdherenceState state, EventStream finalReport, EventStreamAdherenceReport reports) {
        if (finalReport.getByDayEntries().isEmpty()) {
            for (EventStream report : reports.getStreams()) {
                for (List<EventStreamDay> days :report.getByDayEntries().values()) {
                    for (EventStreamDay oneDay : days) {
                        LocalDate startDate = oneDay.getStartDate();
                        // If an activity is "not applicable," then the startDate cannot be determined and
                        // it will be null...and it's not the next activity for this user, so skip it.
                        if (startDate == null) {
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
        }
        return null;
    }
}
