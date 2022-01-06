package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState.Builder;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

import com.google.common.collect.ImmutableList;

public class WeeklyAdherenceReportGenerator {
    
    public static final WeeklyAdherenceReportGenerator INSTANCE = new WeeklyAdherenceReportGenerator();

    public WeeklyAdherenceReport generate(AdherenceState state) {
        
        AdherenceState stateCopy = state.copy();
        EventStreamAdherenceReport reports = EventStreamAdherenceReportGenerator.INSTANCE.generate(stateCopy);
        TestUtils.print(reports);
        EventStream finalReport = new EventStream();

        for (EventStream report : reports.getStreams()) {
            int highestEndDay = highestEndDay(report.getByDayEntries());
            
            Integer currentDay = state.getDaysSinceEventById(report.getStartEventId());
            if (currentDay == null || currentDay < 0) {
                // Participant has not yet begun the activities scheduled by this event
                continue;
            }
            if (currentDay > highestEndDay) {
                // Participant has completed the activities scheduled by this event
                continue;
            }
            int currentWeek = currentDay / 7;    
            int startDayOfWeek = (currentWeek*7);
            int endDayOfWeek = startDayOfWeek+7;
            
            // Object instance identity is used to prevent duplication of entries
            Set<EventStreamDay> selectedDays = new LinkedHashSet<>();
            for (Integer dayInReport : report.getByDayEntries().keySet()) {
                List<EventStreamDay> days = report.getByDayEntries().get(dayInReport);
                for (EventStreamDay oneDay : days) {
                    int startDay = oneDay.getStartDay();
                    for (EventStreamWindow window : oneDay.getTimeWindows()) {
                        int endDay = window.getEndDay();
                        if (startDay <= endDayOfWeek && endDay >= startDayOfWeek) {
                            oneDay.setWeek(currentWeek+1);
                            oneDay.setStudyBurstId(report.getStudyBurstId());
                            oneDay.setStudyBurstNum(report.getStudyBurstNum());
                            selectedDays.add(oneDay);
                        }
                    }
                }
            }
            for (EventStreamDay oneDay : selectedDays) {
                int dayOfWeek = oneDay.getStartDay() - startDayOfWeek;
                // if dayOfWeek is negative, the session started prior to this week and it
                // is still active, so it should still be in the list. Given visual designs,
                // this starts at day 0 (because it started before that).
                if (dayOfWeek < 0) {
                    dayOfWeek = 0;
                }
                finalReport.addEntry(dayOfWeek, oneDay);   
            }
        }
        
        // If the report is empty, then we are asked to seek ahead and store some when
        // the next activity will be required.
        LocalDate nextActivity = state.getNow().plusYears(100).toLocalDate();
        EventStreamDay nextDay = null;
        if (finalReport.getByDayEntries().isEmpty()) {
            for (EventStream report : reports.getStreams()) {
                for (List<EventStreamDay> list :report.getByDayEntries().values()) {
                    for (EventStreamDay day : list) {
                        LocalDate startDate = day.getStartDate();
                        if (startDate == null) {
                            continue;
                        }
                        if (startDate.isBefore(nextActivity) && startDate.isAfter(state.getNow().toLocalDate())) {
                            nextActivity = startDate;
                            nextDay = day;
                            day.setStartDay(null);
                            day.setStudyBurstId(report.getStudyBurstId());
                            day.setStudyBurstNum(report.getStudyBurstNum());
                            for (EventStreamWindow win : day.getTimeWindows()) {
                                win.setEndDay(null);
                            }
                            Integer currentDay = state.getDaysSinceEventById(report.getStartEventId());
                            int maxDays = highestEndDay(report.getByDayEntries());
                            if (currentDay != null && currentDay >= 0 && currentDay <= maxDays) {
                                int currentWeek = currentDay / 7;
                                day.setWeek(currentWeek+1);    
                            }
                        }
                    }
                }
            }
        }

        int percentage = AdherenceUtils.calculateAdherencePercentage(ImmutableList.of(finalReport));

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

        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        report.setByDayEntries(finalReport.getByDayEntries());
        report.setTimestamp(state.getNow());
        report.setClientTimeZone(state.getClientTimeZone());
        report.setWeeklyAdherencePercent(percentage);
        report.setNextActivity(NextActivity.create(nextDay));
        report.setLabels(labels);
        return report;
    }
    
    private int highestEndDay(Map<Integer, List<EventStreamDay>> map) {
        int max = 0;
        for (List<EventStreamDay> oneList : map.values()) {
            for (EventStreamDay day : oneList) {
                for (EventStreamWindow window : day.getTimeWindows()) {
                    if (window.getEndDay().intValue() > max) {
                        max = window.getEndDay().intValue();
                    }
                }
            }
        }
        return max;
    }
}
