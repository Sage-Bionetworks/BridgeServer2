package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.NextActivity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonPropertyOrder({ "participant", "testAccount", "clientTimeZone", "createdOn", "timestamp", "adherencePercent",
        "progression", "dateRange", "weeks", "unsetEventIds", "unscheduledSessions", "eventTimestamps", "type" })
public class StudyAdherenceReport {
    
    private AccountRef participant;
    private boolean testAccount;
    private String clientTimeZone;
    private DateTime createdOn;
    private DateRange dateRange;
    private Integer adherencePercent;
    private ParticipantStudyProgress progression;
    private Set<String> unsetEventIds;
    private Set<String> unscheduledSessions;
    private List<StudyReportWeek> weeks;
    private StudyReportWeek currentWeek;
    private NextActivity nextActivity;
    private Map<String, DateTime> eventTimestamps;
    
    public AccountRef getParticipant() {
        return participant;
    }
    public void setParticipant(AccountRef participant) {
        this.participant = participant;
    }
    public boolean isTestAccount() {
        return testAccount;
    }
    public void setTestAccount(boolean testAccount) {
        this.testAccount = testAccount;
    }
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getCreatedOn() {
        return (createdOn == null) ? null : createdOn.withZone(DateTimeZone.forID(clientTimeZone));
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public Integer getAdherencePercent() {
        return adherencePercent;
    }
    public void setAdherencePercent(Integer adherencePercent) {
        this.adherencePercent = adherencePercent;
    }
    public ParticipantStudyProgress getProgression() {
        return progression;
    }
    public void setProgression(ParticipantStudyProgress progression) {
        this.progression = progression;
    }
    public Set<String> getUnsetEventIds() {
        return unsetEventIds;
    }
    public void setUnsetEventIds(Set<String> unsetEventIds) {
        this.unsetEventIds = unsetEventIds;
    }
    public DateRange getDateRange() {
        return dateRange;
    }
    public void setDateRange(DateRange dateRange) {
        this.dateRange = dateRange;
    }
    public List<StudyReportWeek> getWeeks() {
        return weeks;
    }
    public void setWeeks(List<StudyReportWeek> weeks) {
        this.weeks = weeks;
    }
    public Map<String, DateTime> getEventTimestamps() {
        return eventTimestamps;
    }
    public void setEventTimestamps(Map<String, DateTime> eventTimestamps) {
        this.eventTimestamps = eventTimestamps;
    }
    public Set<String> getUnscheduledSessions() {
        return unscheduledSessions;
    }
    public void setUnscheduledSessions(Set<String> unscheduledSessions) {
        this.unscheduledSessions = unscheduledSessions;
    }
    @JsonIgnore
    public StudyReportWeek getCurrentWeek() {
        return currentWeek;
    }
    public void setCurrentWeek(StudyReportWeek currentWeek) {
        this.currentWeek = currentWeek;
    }
    public NextActivity getNextActivity() {
        return nextActivity;
    }
    public void setNextActivity(NextActivity nextActivity) {
        this.nextActivity = nextActivity;
    }
}
