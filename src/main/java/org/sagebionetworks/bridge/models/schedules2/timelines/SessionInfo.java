package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.BridgeUtils.selectByLang;

import java.util.List;

import org.joda.time.Period;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.NotificationType;
import org.sagebionetworks.bridge.models.schedules2.PerformanceOrder;
import org.sagebionetworks.bridge.models.schedules2.ReminderType;
import org.sagebionetworks.bridge.models.schedules2.Session;

public class SessionInfo {

    private String guid;
    private String name;
    private String label;
    private String startEventId;
    private Period expiration;
    private boolean persistent;
    private PerformanceOrder performanceOrder;
    private NotificationType notifyAt;
    private ReminderType remindAt;
    private Period reminderPeriod;
    private boolean allowSnooze;
    private int minutesToComplete;
    private NotificationMessage message;
    
    public static final SessionInfo create(Session session) {
        List<String> languages = RequestContext.get().getCallerLanguages();
        
        Label label = selectByLang(session.getLabels(), languages, new Label("", session.getName()));
        NotificationMessage msg = selectByLang(session.getMessages(), languages, null);
        
        int min = 0;
        for (AssessmentReference ref : session.getAssessments()) {
            min += ref.getMinutesToComplete();
        }
        SessionInfo info = new SessionInfo();
        info.guid = session.getGuid();
        info.name = session.getName();
        info.label = label.getValue();
        info.startEventId = session.getStartEventId();
        info.performanceOrder = session.getPerformanceOrder();
        info.notifyAt = session.getNotifyAt();
        info.remindAt = session.getRemindAt();
        info.reminderPeriod = session.getReminderPeriod();
        info.allowSnooze = session.isAllowSnooze();
        info.minutesToComplete = min;
        info.message = msg;
        return info;
    }
    
    public String getGuid() {
        return guid;
    }
    public String getName() {
        return name;
    }
    public String getLabel() {
        return label;
    }
    public String getStartEventId() {
        return startEventId;
    }
    public PerformanceOrder getPerformanceOrder() {
        return performanceOrder;
    }
    public NotificationType getNotifyAt() {
        return notifyAt;
    }
    public ReminderType getRemindAt() {
        return remindAt;
    }
    public Period getRemindPeriod() {
        return reminderPeriod;
    }
    public boolean isAllowSnooze() {
        return allowSnooze;
    }
    public int getMinutesToComplete() {
        return minutesToComplete;
    }
    public NotificationMessage getMessage() {
        return message;
    }
   
}
