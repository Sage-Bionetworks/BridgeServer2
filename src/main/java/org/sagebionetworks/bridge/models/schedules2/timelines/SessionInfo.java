package org.sagebionetworks.bridge.models.schedules2.timelines;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeUtils.selectByLang;

import java.util.List;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.PerformanceOrder;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class SessionInfo {

    private String guid;
    private String label;
    private String startEventId;
    private PerformanceOrder performanceOrder;
    private Integer minutesToComplete;
    private List<String> timeWindowGuids;
    private List<NotificationInfo> notifications;
    
    public static final SessionInfo create(Session session) {
        List<String> languages = RequestContext.get().getCallerLanguages();
        
        Label label = selectByLang(session.getLabels(), languages, new Label("", session.getName()));
        
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
        info.timeWindowGuids = session.getTimeWindows().stream()
                .map(TimeWindow::getGuid)
                .collect(toList());
        info.notifications = session.getNotifications().stream()
                .map(not -> NotificationInfo.create(not, languages))
                .collect(toList());
        if (min > 0) {
            info.minutesToComplete = min;    
        }
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
    public Integer getMinutesToComplete() {
        return minutesToComplete;
    }
    public List<String> getTimeWindowGuids() {
        return timeWindowGuids;
    }
    public List<NotificationInfo> getNotifications() { 
        return notifications;
    }
}
