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
    private String label;
    private String startEventId;
    private PerformanceOrder performanceOrder;
    private NotificationType notifyAt;
    private ReminderType remindAt;
    private Period reminderPeriod;
    private Boolean allowSnooze;
    private Integer minutesToComplete;
    private NotificationMessage message;
    
    public static final SessionInfo create(Session session) {
        List<String> languages = RequestContext.get().getCallerLanguages();
        
        Label label = selectByLang(session.getLabels(), languages, new Label("", session.getName()));
        NotificationMessage msg = selectByLang(session.getMessages(), languages, null);
        
        int min = 0;
        for (AssessmentReference ref : session.getAssessments()) {
            if (ref.getMinutesToComplete() != null) {
                min += ref.getMinutesToComplete();    
            }
        }
        SessionInfo info = new SessionInfo();
        info.guid = session.getGuid();
        info.label = label.getValue();
        info.startEventId = session.getStartEventId();
        info.performanceOrder = session.getPerformanceOrder();
        info.notifyAt = session.getNotifyAt();
        info.remindAt = session.getRemindAt();
        info.reminderPeriod = session.getReminderPeriod();
        if (session.isAllowSnooze()) {
            info.allowSnooze = Boolean.TRUE;    
        }
        if (min > 0) {
            info.minutesToComplete = min;    
        }
        info.message = msg;
        return info;
    }
    
    public String getGuid() {
        return guid;
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
    public Period getReminderPeriod() {
        return reminderPeriod;
    }
    public Boolean isAllowSnooze() {
        return allowSnooze;
    }
    public Integer getMinutesToComplete() {
        return minutesToComplete;
    }
    public NotificationMessage getMessage() {
        return message;
    }
}
