package org.sagebionetworks.bridge.models.schedules2;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.Period;

import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "ScheduleSessions")
@BridgeTypeName("Session")
public class Session implements BridgeEntity {
    
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
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "AssessmentReferences", 
        joinColumns = @JoinColumn(name = "sessionGuid", nullable = false))
    @OrderColumn(name = "position")
    private List<AssessmentReference> assessments;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ScheduleSessionTimeWindows", 
        joinColumns = @JoinColumn(name = "sessionGuid", nullable = false))
    @OrderColumn(name = "position")
    private List<TimeWindow> timeWindows;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ScheduleSessionMessages", 
        joinColumns = @JoinColumn(name = "sessionGuid", nullable = false))
    private List<Message> messages;
    
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
    public List<AssessmentReference> getAssessments() {
        if (assessments == null) {
            assessments = new ArrayList<>();
        }
        return assessments;
    }
    public void setAssessments(List<AssessmentReference> assessments) {
        this.assessments = assessments;
    }
    public List<TimeWindow> getTimeWindows() {
        if (timeWindows == null) {
            timeWindows = new ArrayList<>();
        }
        return timeWindows;
    }
    public void setTimeWindows(List<TimeWindow> timeWindows) {
        this.timeWindows = timeWindows;
    }
    public List<Message> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}
