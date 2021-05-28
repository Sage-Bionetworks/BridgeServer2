package org.sagebionetworks.bridge.models.schedules2;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.NotificationMessageListConverter;
import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

@Embeddable
@Table(name = "SessionNotifications")
public class Notification {
    
    @Enumerated(EnumType.STRING)
    private NotificationType notifyAt;
    
    @Convert(converter = PeriodToStringConverter.class)
    @Column(name = "offsetPeriod")
    private Period offset;
    
    @Convert(converter = PeriodToStringConverter.class)
    @Column(name = "intervalPeriod")
    private Period interval;
    
    private Boolean allowSnooze = Boolean.FALSE;
    
    @Column(columnDefinition = "text", name = "messages", nullable = true)
    @Convert(converter = NotificationMessageListConverter.class)
    private List<NotificationMessage> messages;

    public NotificationType getNotifyAt() {
        return notifyAt;
    }
    public void setNotifyAt(NotificationType notifyAt) {
        this.notifyAt = notifyAt;
    }
    public Period getOffset() {
        return offset;
    }
    public void setOffset(Period offset) {
        this.offset = offset;
    }
    public Period getInterval() {
        return interval;
    }
    public void setInterval(Period interval) {
        this.interval = interval;
    }
    public Boolean getAllowSnooze() {
        return allowSnooze;
    }
    public void setAllowSnooze(Boolean allowSnooze) {
        this.allowSnooze = allowSnooze;
    }
    public List<NotificationMessage> getMessages() {
        return messages;
    }
    public void setMessages(List<NotificationMessage> messages) {
        this.messages = messages;
    }    
}
