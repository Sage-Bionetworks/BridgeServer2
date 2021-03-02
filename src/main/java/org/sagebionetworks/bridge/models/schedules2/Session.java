package org.sagebionetworks.bridge.models.schedules2;

import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.schedules.Schedule;

@Entity
@Table(name = "ScheduleSessions")
@BridgeTypeName("Session")
public class Session {
    
    @ManyToOne
    @JoinColumn(name = "scheduleGuid", nullable = false)
    @JsonIgnore
    private Schedule2 schedule;
    
    @Id
    private String guid;
    @JsonIgnore
    private int position;
    private String name;
    private String startEventId;
    @Convert(converter = PeriodToStringConverter.class)
    @Column(name = "delayPeriod")
    private Period delay;
    private Integer occurrences;
    @Convert(converter = PeriodToStringConverter.class)
    @Column(name = "intervalPeriod")
    private Period interval;
    private boolean bundled;
    private boolean randomized;
    @Enumerated(EnumType.STRING)
    private NotificationType notifyAt;
    @Enumerated(EnumType.STRING)
    private ReminderType remindAt;
    private Integer remindMinBefore;
    private boolean allowSnooze;
    @Transient
    private List<Task> assessments;
    @Transient
    private List<SessionWindow> sessionWindows;
    @Transient
    private Map<String,Message> messagesByLocale;
    
    public Schedule2 getSchedule() {
        return schedule;
    }
    public void setSchedule(Schedule2 schedule) {
        this.schedule = schedule;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public int getPosition() {
        return position;
    }
    public void setPosition(int position) {
        this.position = position;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getStartEventId() {
        return startEventId;
    }
    public void setStartEventId(String startEventId) {
        this.startEventId = startEventId;
    }
    public Period getDelay() {
        return delay;
    }
    public void setDelay(Period delay) {
        this.delay = delay;
    }
    public Integer getOccurrences() {
        return occurrences;
    }
    public void setOccurrences(Integer occurrences) {
        this.occurrences = occurrences;
    }
    public Period getInterval() {
        return interval;
    }
    public void setInterval(Period interval) {
        this.interval = interval;
    }
    public boolean isBundled() {
        return bundled;
    }
    public void setBundled(boolean bundled) {
        this.bundled = bundled;
    }
    public boolean isRandomized() {
        return randomized;
    }
    public void setRandomized(boolean randomized) {
        this.randomized = randomized;
    }
    public NotificationType getNotifyAt() {
        return notifyAt;
    }
    public void setNotifyAt(NotificationType notifyAt) {
        this.notifyAt = notifyAt;
    }
    public ReminderType getRemindAt() {
        return remindAt;
    }
    public void setRemindAt(ReminderType remindAt) {
        this.remindAt = remindAt;
    }
    public Integer getRemindMinBefore() {
        return remindMinBefore;
    }
    public void setRemindMinBefore(Integer remindMinBefore) {
        this.remindMinBefore = remindMinBefore;
    }
    public boolean isAllowSnooze() {
        return allowSnooze;
    }
    public void setAllowSnooze(boolean allowSnooze) {
        this.allowSnooze = allowSnooze;
    }
    public List<Task> getAssessments() {
        return assessments;
    }
    public void setAssessments(List<Task> assessments) {
        this.assessments = assessments;
    }
    public List<SessionWindow> getSessionWindows() {
        return sessionWindows;
    }
    public void setSessionWindows(List<SessionWindow> sessionWindows) {
        this.sessionWindows = sessionWindows;
    }
    public Map<String, Message> getMessagesByLocale() {
        return messagesByLocale;
    }
    public void setMessagesByLocale(Map<String, Message> messagesByLocale) {
        this.messagesByLocale = messagesByLocale;
    }
}
