package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantProgressionState;

public final class AdherenceReportSearch implements BridgeEntity {
    private AccountTestFilter testFilter;
    private List<String> labelFilters; 
    private Integer adherenceMin = 0;
    private Integer adherenceMax = 100;
    private ParticipantProgressionState progressionFilter;
    private String idFilter;
    private Integer offsetBy = 0; 
    private Integer pageSize = API_DEFAULT_PAGE_SIZE;
    
    public AccountTestFilter getTestFilter() {
        return testFilter;
    }
    public void setTestFilter(AccountTestFilter testFilter) {
        this.testFilter = testFilter;
    }
    public List<String> getLabelFilters() {
        return labelFilters;
    }
    public void setLabelFilters(List<String> labelFilters) {
        this.labelFilters = labelFilters;
    }
    public Integer getAdherenceMin() {
        return adherenceMin;
    }
    public void setAdherenceMin(Integer adherenceMin) {
        if (adherenceMin != null) {
            this.adherenceMin = adherenceMin;    
        }
    }
    public Integer getAdherenceMax() {
        return adherenceMax;
    }
    public void setAdherenceMax(Integer adherenceMax) {
        if (adherenceMax != null) {
            this.adherenceMax = adherenceMax;    
        }
    }
    public ParticipantProgressionState getProgressionFilter() {
        return progressionFilter;
    }
    public void setProgressionFilter(ParticipantProgressionState progressionFilter) {
        this.progressionFilter = progressionFilter;
    }
    public String getIdFilter() {
        return idFilter;
    }
    public void setIdFilter(String idFilter) {
        this.idFilter = idFilter;
    }
    public Integer getOffsetBy() {
        return offsetBy;
    }
    public void setOffsetBy(Integer offsetBy) {
        if (offsetBy != null) {
            this.offsetBy = offsetBy;
        }
    }
    public Integer getPageSize() {
        return pageSize;
    }
    public void setPageSize(Integer pageSize) {
        if (pageSize != null) {
            this.pageSize = pageSize;    
        }
    }
    @Override
    public int hashCode() {
        return Objects.hash(adherenceMin, adherenceMax, idFilter, labelFilters, offsetBy, pageSize, progressionFilter,
                testFilter);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AdherenceReportSearch other = (AdherenceReportSearch) obj;
        return Objects.equals(adherenceMin, other.adherenceMin) && Objects.equals(adherenceMax, other.adherenceMax)
                && Objects.equals(idFilter, other.idFilter) && Objects.equals(labelFilters, other.labelFilters)
                && Objects.equals(offsetBy, other.offsetBy) && Objects.equals(pageSize, other.pageSize)
                && progressionFilter == other.progressionFilter && testFilter == other.testFilter;
    }
}
