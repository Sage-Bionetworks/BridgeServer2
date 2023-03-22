package org.sagebionetworks.bridge.models.schedules2.adherence.detailed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.accounts.AccountRef;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DetailedAdherenceReport {
    private AccountRef participant;
    private boolean testAccount;
    private String clientTimeZone;
    private DateTime joinedDate;
    private Map<String, DetailedAdherenceReportSessionRecord> sessionRecords;
    
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
    
    public DateTime getJoinedDate() {
        return joinedDate;
    }
    
    public void setJoinedDate(DateTime joinedDate) {
        this.joinedDate = joinedDate;
    }
    
    public String getClientTimeZone() {
        return clientTimeZone;
    }
    
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
    
    public List<DetailedAdherenceReportSessionRecord> getSessionRecords() {
        return ImmutableList.copyOf(sessionRecords.values().stream().sorted(new Comparator<DetailedAdherenceReportSessionRecord>() {
            @Override
            public int compare(DetailedAdherenceReportSessionRecord rec1, DetailedAdherenceReportSessionRecord rec2) {
                return ComparisonChain.start()
                        .compare(rec1.getSortPriority(), rec2.getSortPriority())
                        .compare(rec1.getSessionStart(), rec2.getSessionStart(), Ordering.natural().nullsLast())
                        .compare(rec1.getSessionInstanceGuid(), rec2.getSessionInstanceGuid())
                        .result();
            }
        }).collect(Collectors.toList()));
    }
    
    public void setSessionRecords(Map<String, DetailedAdherenceReportSessionRecord> sessionRecords) {
        this.sessionRecords = sessionRecords;
    }
    
    @JsonIgnore
    public Map<String, DetailedAdherenceReportSessionRecord> getSessionRecordMap() {
        return this.sessionRecords;
    }
}
