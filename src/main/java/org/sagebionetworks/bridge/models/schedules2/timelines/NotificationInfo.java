package org.sagebionetworks.bridge.models.schedules2.timelines;

import java.util.List;

import org.joda.time.Period;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.schedules2.Notification;
import org.sagebionetworks.bridge.models.schedules2.NotificationType;

/**
 * A localized instance of a notification, provided as part of a Timeline.
 */
public class NotificationInfo {

    public static final NotificationInfo create(Notification notification, List<String> languages) {
        NotificationMessage msg = BridgeUtils.selectByLang(notification.getMessages(), languages, null);
        NotificationInfo info = new NotificationInfo();
        info.notifyAt = notification.getNotifyAt();
        info.offset = notification.getOffset();
        info.interval = notification.getInterval();
        info.allowSnooze = notification.getAllowSnooze();
        info.message = msg;
        return info;
    }

    private NotificationType notifyAt;
    private Period offset;
    private Period interval;
    private Boolean allowSnooze;
    private NotificationMessage message;
    
    public NotificationType getNotifyAt() {
        return notifyAt;
    }
    public Period getOffset() {
        return offset;
    }
    public Period getInterval() {
        return interval;
    }
    public Boolean getAllowSnooze() {
        return allowSnooze;
    }
    public NotificationMessage getMessage() {
        return message;
    }
}
