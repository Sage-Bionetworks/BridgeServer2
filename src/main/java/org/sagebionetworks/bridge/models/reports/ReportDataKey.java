package org.sagebionetworks.bridge.models.reports;

import java.util.Objects;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A key object for reports, which come in two types: participant and study. Participant 
 * reports can include reports for every participant in a study, while study reports will
 * produce one report for an entire study (each at one or more LocalDates). All values are 
 * required except healthCode, which is only required if the report type is PARTICIPANT.
 */
public final class ReportDataKey implements BridgeEntity {
    
    private final String appId;
    private final String identifier;
    private final String healthCode;
    private final ReportType reportType;
    
    private ReportDataKey(String healthCode, String identifier, String appId, ReportType reportType) {
        this.appId = appId;
        this.identifier = identifier;
        this.healthCode = healthCode;
        this.reportType = reportType;
    }
    
    @JsonIgnore
    public String getAppId() {
        return appId;
    }

    public String getIdentifier() {
        return identifier;
    }

    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    public ReportType getReportType() {
        return reportType;
    }
    
    @JsonIgnore
    public String getKeyString() {
        return (healthCode != null) ?
                String.format("%s:%s:%s", healthCode, identifier, appId) :
                String.format("%s:%s", identifier, appId);
    }
    
    @JsonIgnore
    public String getIndexKeyString() {
        return String.format("%s:%s", appId, reportType.name());
    }
    
    @Override
    public String toString() {
        return "ReportDataKey [appId=" + appId + ", identifier=" + identifier
                + ", healthCode=[REDACTED], reportType=" + reportType + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(healthCode, identifier, appId, reportType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ReportDataKey other = (ReportDataKey) obj;
        return Objects.equals(healthCode, other.healthCode) && Objects.equals(identifier, other.identifier)
                && Objects.equals(appId, other.appId) && Objects.equals(reportType, other.reportType);
    }
    
    public static class Builder {
        private String appId;
        private String identifier;
        private String healthCode;
        private ReportType reportType;
        
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withReportType(ReportType reportType) {
            this.reportType = reportType;
            return this;
        }
        public ReportDataKey build() {
            return new ReportDataKey(healthCode, identifier, appId, reportType);
        }
    }
    
}
