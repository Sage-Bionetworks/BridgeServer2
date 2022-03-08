package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.EventStreamDayMapConverter;
import org.sagebionetworks.bridge.hibernate.NextActivityConverter;
import org.sagebionetworks.bridge.hibernate.WeeklyAdherenceReportRowListConverter;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Entity
@Table(name = "WeeklyAdherenceReports")
@IdClass(WeeklyAdherenceReportId.class)
@JsonPropertyOrder({ "participant", "testAccount", "progression", "weeklyAdherencePercent", "week", "clientTimeZone",
        "createdOn", "rows", "byDayEntries", "type" })
public class WeeklyAdherenceReport {
    
    @Id
    private String appId;
    @Id
    private String studyId;
    @Id
    private String userId;
    @Embedded
    private AccountRef participant;
    @Enumerated(EnumType.STRING)
    private ParticipantStudyProgress progression;
    private boolean testAccount;
    private String clientTimeZone;
    private Integer weeklyAdherencePercent;
    private Integer weekInStudy;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = NextActivityConverter.class)
    private NextActivity nextActivity;
    @Convert(converter = EventStreamDayMapConverter.class)
    private Map<Integer, List<EventStreamDay>> byDayEntries;
    @CollectionTable(name = "WeeklyAdherenceReportLabels", joinColumns = {
        @JoinColumn(name = "appId"), @JoinColumn(name = "studyId"), @JoinColumn(name = "userId")})
    @Column(name = "label")
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> searchableLabels;
    @Convert(converter = WeeklyAdherenceReportRowListConverter.class)
    List<WeeklyAdherenceReportRow> rows;
    
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
    public ParticipantStudyProgress getProgression() {
        return progression;
    }
    public void setProgression(ParticipantStudyProgress progression) {
        this.progression = progression;
    }
    public boolean isTestAccount() {
        return testAccount;
    }
    public void setTestAccount(boolean testAccount) {
        this.testAccount = testAccount;
    }
    public Integer getWeeklyAdherencePercent() {
        return weeklyAdherencePercent;
    }
    public void setWeeklyAdherencePercent(Integer weeklyAdherencePercent) {
        this.weeklyAdherencePercent = weeklyAdherencePercent;
    }
    public Integer getWeekInStudy() {
        return weekInStudy;
    }
    public void setWeekInStudy(Integer weekInStudy) {
        this.weekInStudy = weekInStudy;
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
    /**
     * Reports contain multiple rows with composite search information (e.g. study burst
     * foo, iteration 2, possibly even a specific week of that study burst). To make
     * the search API for these reports simpler, we're combining this information into
     * string descriptors that can be specified via API search, e.g. "study burst 2" + 
     * "Week 2". These are not displayed as a group for the whole report, they are 
     * shown in the row descriptors. But they are persisted as a collection on the report
     * for the SQL to retrieve records. 
     */
    @JsonIgnore
    public Set<String> getSearchableLabels() {
        return searchableLabels;
    }
    public void setSearchableLabels(Set<String> labels) {
        this.searchableLabels = labels;
    }
    public List<WeeklyAdherenceReportRow> getRows() {
        return rows;
    }
    public void setRows(List<WeeklyAdherenceReportRow> rows) {
        this.rows = rows;
    }
}

