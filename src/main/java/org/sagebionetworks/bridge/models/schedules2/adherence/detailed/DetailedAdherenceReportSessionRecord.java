package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DetailedAdherenceReportSessionRecord {
    private String burstName;
    private String burstId;
    private String sessionName;
    private String sessionGuid;
    private String sessionInstanceGuid;
    private SessionCompletionState sessionStatus;
    private DateTime sessionStart;
    private DateTime sessionCompleted;
    private DateTime sessionExpiration;
    private Map<String, DetailedAdherenceReportAssessmentRecord> assessmentRecords = new HashMap<>();
    private int sortPriority;
    
    public String getBurstName() {
        return burstName;
    }
    
    public void setBurstName(String burstName) {
        this.burstName = burstName;
    }
    
    public String getBurstId() {
        return burstId;
    }
    
    public void setBurstId(String burstId) {
        this.burstId = burstId;
    }
    
    public String getSessionName() {
        return sessionName;
    }
    
    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    
    public String getSessionGuid() {
        return sessionGuid;
    }
    
    public void setSessionGuid(String sessionGuid) {
        this.sessionGuid = sessionGuid;
    }
    
    public String getSessionInstanceGuid() {
        return sessionInstanceGuid;
    }
    
    public void setSessionInstanceGuid(String sessionInstanceGuid) {
        this.sessionInstanceGuid = sessionInstanceGuid;
    }
    
    public SessionCompletionState getSessionStatus() {
        return sessionStatus;
    }
    
    public void setSessionStatus(SessionCompletionState sessionStatus) {
        this.sessionStatus = sessionStatus;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getSessionStart() {
        return sessionStart;
    }
    
    public void setSessionStart(DateTime sessionStart) {
        this.sessionStart = sessionStart;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getSessionCompleted() {
        return sessionCompleted;
    }
    
    public void setSessionCompleted(DateTime sessionCompleted) {
        this.sessionCompleted = sessionCompleted;
    }
    
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getSessionExpiration() {
        return sessionExpiration;
    }
    
    public void setSessionExpiration(DateTime sessionExpiration) {
        this.sessionExpiration = sessionExpiration;
    }
    
    public List<DetailedAdherenceReportAssessmentRecord> getAssessmentRecords() {
        return ImmutableList.copyOf(assessmentRecords.values().stream().sorted(new Comparator<DetailedAdherenceReportAssessmentRecord>() {
            @Override
            public int compare(DetailedAdherenceReportAssessmentRecord rec1, DetailedAdherenceReportAssessmentRecord rec2) {
                return ComparisonChain.start()
                        .compare(rec1.getSortPriority(), rec2.getSortPriority())
                        .compare(rec1.getAssessmentStart(), rec2.getAssessmentStart(), Ordering.natural().nullsLast())
                        .compare(rec1.getAssessmentInstanceGuid(), rec2.getAssessmentInstanceGuid())
                        .result();
            }
        }).collect(Collectors.toList()));
    }
    
    public void setAssessmentRecords(Map<String, DetailedAdherenceReportAssessmentRecord> assessmentRecords) {
        this.assessmentRecords = assessmentRecords;
    }
    
    @JsonIgnore
    public Map<String, DetailedAdherenceReportAssessmentRecord> getAssessmentRecordMap() {
        return assessmentRecords;
    }
    
    @JsonIgnore
    public int getSortPriority() {
        return sortPriority;
    }
    
    public void setSortPriority(int sortPriority) {
        this.sortPriority = sortPriority;
    }
}
