package org.sagebionetworks.bridge.models.studies;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;

/**
 * This object is used to communicate study information from the API to consumers,
 * but it has no setters because we continue to deserialize updates to the Study
 * object using the Study objects implementation. This object allows us to send
 * appropriate account information for the "actionBy" fields.
 */
@BridgeTypeName("Study")
public final class StudyDetail {

    private String identifier;
    private String name;
    private boolean deleted;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private JsonNode clientData;
    private AccountRef createdBy;
    private AccountRef modifiedBy;
    private String details;
    private DateTime launchedOn;
    private AccountRef launchedBy;
    private DateTime closeoutOn;
    private AccountRef closeoutBy;
    private DateTime irbApprovedOn;
    private String studyLogoUrl;
    private ColorScheme colorScheme;
    private String externalProtocolId;
    private String irbProtocolId;
    private String scheduleGuid;
    private Long version;
    
    public String getIdentifier() {
        return identifier;
    }
    public String getName() {
        return name;
    }
    public boolean isDeleted() {
        return deleted;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public AccountRef getCreatedBy() {
        return createdBy;
    }
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    public AccountRef getModifiedBy() {
        return modifiedBy;
    }
    public JsonNode getClientData() {
        return clientData;
    }
    public String getDetails() {
        return details;
    }
    public DateTime getLaunchedOn() {
        return launchedOn;
    }
    public AccountRef getLaunchedBy() {
        return launchedBy;
    }
    public DateTime getCloseoutOn() {
        return closeoutOn;
    }
    public AccountRef getCloseoutBy() {
        return closeoutBy;
    }
    public DateTime getIrbApprovedOn() {
        return irbApprovedOn;
    }
    public String getStudyLogoUrl() {
        return studyLogoUrl;
    }
    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    public String getExternalProtocolId() {
        return externalProtocolId;
    }
    public String getIrbProtocolId() {
        return irbProtocolId;
    }
    public String getScheduleGuid() {
        return scheduleGuid;
    }
    public Long getVersion() {
        return version;
    }
    
    public static class Builder {
        private Study study;
        private AccountRef launchedBy;
        private AccountRef closeoutBy;
        private AccountRef createdBy;
        private AccountRef modifiedBy;
        
        public Builder withStudy(Study study) {
            this.study = study;
            return this;
        }
        public Builder withLaunchedBy(AccountRef launchedBy) {
            this.launchedBy = launchedBy;
            return this;
        }
        public Builder withCloseoutBy(AccountRef closeoutBy) {
            this.closeoutBy = closeoutBy;
            return this;
        }
        public Builder withCreatedBy(AccountRef createdBy) {
            this.createdBy = createdBy;
            return this;
        }
        public Builder withModifiedBy(AccountRef modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }
        
        public StudyDetail build() {
            StudyDetail detail = new StudyDetail();
            detail.identifier = study.getIdentifier();
            detail.name = study.getName();
            detail.deleted = study.isDeleted();
            detail.createdOn = study.getCreatedOn();
            detail.modifiedOn = study.getModifiedOn();
            detail.clientData = study.getClientData();
            detail.createdBy = createdBy;
            detail.modifiedBy = modifiedBy;
            detail.details = study.getDetails();
            detail.launchedOn = study.getLaunchedOn();
            detail.launchedBy = launchedBy;
            detail.closeoutOn = study.getCloseoutOn();
            detail.closeoutBy = closeoutBy;
            detail.irbApprovedOn = study.getIrbApprovedOn();
            detail.studyLogoUrl = study.getStudyLogoUrl();
            detail.colorScheme = study.getColorScheme();
            detail.externalProtocolId = study.getExternalProtocolId();
            detail.irbProtocolId = study.getIrbProtocolId();
            detail.scheduleGuid = study.getScheduleGuid();
            detail.version = study.getVersion();
            return detail;
        }
    }
    
}
