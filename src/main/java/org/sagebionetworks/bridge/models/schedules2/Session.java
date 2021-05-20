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

import org.sagebionetworks.bridge.hibernate.LabelListConverter;
import org.sagebionetworks.bridge.hibernate.PeriodToStringConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Label;

@Entity
@Table(name = "Sessions")
@BridgeTypeName("Session")
public class Session implements BridgeEntity, HasGuid {
    
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
    @Enumerated(EnumType.STRING)
    private PerformanceOrder performanceOrder;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "SessionAssessments", 
        joinColumns = @JoinColumn(name = "sessionGuid", nullable = false))
    @OrderColumn(name = "position")
    private List<AssessmentReference> assessments;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "SessionTimeWindows", 
        joinColumns = @JoinColumn(name = "sessionGuid", nullable = false))
    @OrderColumn(name = "position")
    private List<TimeWindow> timeWindows;
    
    @Column(columnDefinition = "text", name = "labels", nullable = true)
    @Convert(converter = LabelListConverter.class)
    private List<Label> labels;

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
    public List<Label> getLabels() {
        if (labels == null) {
            labels = new ArrayList<>();
        }
        return labels;
    }
    public void setLabels(List<Label> labels) {
        this.labels = labels;
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
    public PerformanceOrder getPerformanceOrder() {
        return performanceOrder;
    }
    public void setPerformanceOrder(PerformanceOrder performanceOrder) {
        this.performanceOrder = performanceOrder;
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
}
