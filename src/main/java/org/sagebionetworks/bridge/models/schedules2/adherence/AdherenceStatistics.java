package org.sagebionetworks.bridge.models.schedules2.adherence;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "adherenceThresholdPercentage", "compliant", "noncompliant", "totalActive", "entries", "type" })
public class AdherenceStatistics {

    private Integer adherenceThresholdPercentage;
    private Integer noncompliant;
    private Integer compliant;
    private Integer totalActive;
    private List<AdherenceStatisticsEntry> entries = new ArrayList<>();
    
    public Integer getAdherenceThresholdPercentage() {
        return adherenceThresholdPercentage;
    }
    public void setAdherenceThresholdPercentage(Integer adherenceThresholdPercentage) {
        this.adherenceThresholdPercentage = adherenceThresholdPercentage;
    }
    public Integer getNoncompliant() {
        return noncompliant;
    }
    public void setNoncompliant(Integer noncompliant) {
        this.noncompliant = noncompliant;
    }
    public Integer getCompliant() {
        return compliant;
    }
    public void setCompliant(Integer compliant) {
        this.compliant = compliant;
    }
    public Integer getTotalActive() {
        return totalActive;
    }
    public void setTotalActive(Integer totalActive) {
        this.totalActive = totalActive;
    }
    public List<AdherenceStatisticsEntry> getEntries() {
        return entries;
    }
    public void setEntries(List<AdherenceStatisticsEntry> entries) {
        this.entries = entries;
    }
    
}
