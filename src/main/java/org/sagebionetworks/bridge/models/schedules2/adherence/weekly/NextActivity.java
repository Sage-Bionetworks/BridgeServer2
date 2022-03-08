package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;

public class NextActivity {

    public static NextActivity create(EventStreamDay day) {
        if (day == null) {
            return null;
        }
        NextActivity activity = new NextActivity();
        activity.label = day.getLabel();
        activity.sessionGuid = day.getSessionGuid();
        activity.sessionName = day.getSessionName();
        activity.sessionSymbol = day.getSessionSymbol();
        activity.weekInStudy = day.getWeek();
        activity.studyBurstId = day.getStudyBurstId();
        activity.studyBurstNum = day.getStudyBurstNum();
        activity.startDate = day.getStartDate();
        return activity;
    }
    
    private NextActivity() {
    }

    private String label;
    private String sessionGuid;
    private String sessionName;
    private String sessionSymbol;
    private Integer weekInStudy;
    private String studyBurstId;
    private Integer studyBurstNum;
    private LocalDate startDate;

    public String getLabel() {
        return label;
    }
    public String getSessionGuid() {
        return sessionGuid;
    }
    public String getSessionName() {
        return sessionName;
    }
    public String getSessionSymbol() {
        return sessionSymbol;
    }
    public Integer getWeekInStudy() {
        return weekInStudy;
    }
    public String getStudyBurstId() {
        return studyBurstId;
    }
    public Integer getStudyBurstNum() {
        return studyBurstNum;
    }
    public LocalDate getStartDate() {
        return startDate;
    }
}
