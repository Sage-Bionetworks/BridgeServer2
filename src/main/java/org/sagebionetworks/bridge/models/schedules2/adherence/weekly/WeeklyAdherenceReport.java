package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.hibernate.AccountRefConverter;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.EventStreamDayMapConverter;
import org.sagebionetworks.bridge.hibernate.NextActivityConverter;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Entity
@Table(name = "WeeklyAdherenceReports")
@IdClass(WeeklyAdherenceReportId.class)
@JsonPropertyOrder({ "participant", "rowLabels", "weeklyAdherencePercent", "clientTimeZone", "createdOn",
        "byDayEntries", "type" })
public class WeeklyAdherenceReport {
    
    @Id
    private String appId;
    @Id
    private String studyId;
    @Id
    private String userId;
    @Convert(converter = AccountRefConverter.class)
    private AccountRef participant;
    private String clientTimeZone;
    private int weeklyAdherencePercent;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = NextActivityConverter.class)
    private NextActivity nextActivity;
    @Convert(converter = EventStreamDayMapConverter.class)
    private Map<Integer, List<EventStreamDay>> byDayEntries;
    
    @CollectionTable(name = "WeeklyAdherenceReportLabels", joinColumns = {
        @JoinColumn(name = "appId"), @JoinColumn(name = "studyId"), @JoinColumn(name = "userId") 
    })
    @Column(name = "label")
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> labels;
    
    public WeeklyAdherenceReport() {
        byDayEntries = new HashMap<>();    
    }
    @JsonIgnore
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    @JsonIgnore
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    @JsonIgnore
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }    
    public AccountRef getParticipant() {
        return participant;
    }
    public void setParticipant(AccountRef participant) {
        this.participant = participant;
    }
    public int getWeeklyAdherencePercent() {
        return weeklyAdherencePercent;
    }
    public void setWeeklyAdherencePercent(int weeklyAdherencePercent) {
        this.weeklyAdherencePercent = weeklyAdherencePercent;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getCreatedOn() {
        return (clientTimeZone == null) ? createdOn : 
            createdOn.withZone(DateTimeZone.forID(clientTimeZone));
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public Map<Integer, List<EventStreamDay>> getByDayEntries() {
        return byDayEntries;
    }
    public void setByDayEntries(Map<Integer, List<EventStreamDay>> byDayEntries) {
        this.byDayEntries = byDayEntries;
    }
    public NextActivity getNextActivity() {
        return nextActivity;
    }
    public void setNextActivity(NextActivity nextActivity) {
        this.nextActivity = nextActivity;
    }
    @JsonProperty("rowLabels")
    public Set<String> getLabels() {
        return labels;
    }
    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }
}

