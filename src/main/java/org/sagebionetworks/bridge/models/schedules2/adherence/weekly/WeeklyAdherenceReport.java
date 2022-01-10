package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Transient;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonPropertyOrder({ "participant", "timestamp", "clientTimeZone", "weeklyAdherencePercent", "labels", "createdOn",
        "byDayEntries", "nextActivity", "type" })
public class WeeklyAdherenceReport {
    
    private String appId;
    private String userId;
    private String studyId;
    @Transient
    private AccountRef participant;
    private String clientTimeZone;
    private int weeklyAdherencePercent;
    private DateTime createdOn;
    private NextActivity nextActivity;
    private Map<Integer, List<EventStreamDay>> byDayEntries;
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
    @JsonSerialize(using = DateTimeSerializer.class) // preserve time zone offset
    public DateTime getCreatedOn() {
        return createdOn;
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

