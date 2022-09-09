package org.sagebionetworks.bridge.models.apps;

/** This class holds Exporter 3.0 configuration for a given app. */
public final class Exporter3Configuration {
    private String createStudyNotificationTopicArn;
    private Long dataAccessTeamId;
    private String participantVersionTableId;
    private String participantVersionDemographicsTableId;
    private String participantVersionDemographicsViewId;
    private String projectId;
    private String rawDataFolderId;
    private Long storageLocationId;

    /** Helper method that returns true if all configuration attributes are specified. */
    public boolean isConfigured() {
        return dataAccessTeamId != null && participantVersionTableId != null && projectId != null &&
                rawDataFolderId != null && storageLocationId != null;
    }

    /** SNS topic to publish to when a study is initialized in this app. (This is not used for study configs.) */
    public String getCreateStudyNotificationTopicArn() {
        return createStudyNotificationTopicArn;
    }

    public void setCreateStudyNotificationTopicArn(String createStudyNotificationTopicArn) {
        this.createStudyNotificationTopicArn = createStudyNotificationTopicArn;
    }

    /**
     * Synapse team ID that is granted read access to exported health data records. This can be the same as, or
     * different than, the data access team for Exporter 2.0.
     */
    public Long getDataAccessTeamId() {
        return dataAccessTeamId;
    }

    public void setDataAccessTeamId(Long dataAccessTeamId) {
        this.dataAccessTeamId = dataAccessTeamId;
    }

    /** The Synapse table to where we export Participant Versions. */
    public String getParticipantVersionTableId() {
        return participantVersionTableId;
    }

    public void setParticipantVersionTableId(String participantVersionTableId) {
        this.participantVersionTableId = participantVersionTableId;
    }

    /** The Synapse table to where we exporter Participant Version Demographics. */
    public String getParticipantVersionDemographicsTableId() {
        return participantVersionDemographicsTableId;
    }

    public void setParticipantVersionDemographicsTableId(String participantVersionDemographicsTableId) {
        this.participantVersionDemographicsTableId = participantVersionDemographicsTableId;
    }

    /** The Synapse materialized view which joins the Participant Versions table and the Demographics table. */
    public String getParticipantVersionDemographicsViewId() {
        return participantVersionDemographicsViewId;
    }

    public void setParticipantVersionDemographicsViewId(String participantVersionDemographicsViewId) {
        this.participantVersionDemographicsViewId = participantVersionDemographicsViewId;
    }

    /**
     * The Synapse project to export health data records to. It is recommended (but not required) that this is a
     * different project than the one used for Exporter 2.0/
     */
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /** The Synapse folder to export raw health data to. */
    public String getRawDataFolderId() {
        return rawDataFolderId;
    }

    public void setRawDataFolderId(String rawDataFolderId) {
        this.rawDataFolderId = rawDataFolderId;
    }

    /**
     * The Synapse storage location that represents our External S3 bucket. This storage location should be
     * STS-enabled. */
    public Long getStorageLocationId() {
        return storageLocationId;
    }

    public void setStorageLocationId(Long storageLocationId) {
        this.storageLocationId = storageLocationId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((createStudyNotificationTopicArn == null) ? 0 : createStudyNotificationTopicArn.hashCode());
        result = prime * result + ((dataAccessTeamId == null) ? 0 : dataAccessTeamId.hashCode());
        result = prime * result + ((participantVersionDemographicsTableId == null) ? 0
                : participantVersionDemographicsTableId.hashCode());
        result = prime * result + ((participantVersionDemographicsViewId == null) ? 0
                : participantVersionDemographicsViewId.hashCode());
        result = prime * result + ((participantVersionTableId == null) ? 0 : participantVersionTableId.hashCode());
        result = prime * result + ((projectId == null) ? 0 : projectId.hashCode());
        result = prime * result + ((rawDataFolderId == null) ? 0 : rawDataFolderId.hashCode());
        result = prime * result + ((storageLocationId == null) ? 0 : storageLocationId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Exporter3Configuration other = (Exporter3Configuration) obj;
        if (createStudyNotificationTopicArn == null) {
            if (other.createStudyNotificationTopicArn != null)
                return false;
        } else if (!createStudyNotificationTopicArn.equals(other.createStudyNotificationTopicArn))
            return false;
        if (dataAccessTeamId == null) {
            if (other.dataAccessTeamId != null)
                return false;
        } else if (!dataAccessTeamId.equals(other.dataAccessTeamId))
            return false;
        if (participantVersionDemographicsTableId == null) {
            if (other.participantVersionDemographicsTableId != null)
                return false;
        } else if (!participantVersionDemographicsTableId.equals(other.participantVersionDemographicsTableId))
            return false;
        if (participantVersionDemographicsViewId == null) {
            if (other.participantVersionDemographicsViewId != null)
                return false;
        } else if (!participantVersionDemographicsViewId.equals(other.participantVersionDemographicsViewId))
            return false;
        if (participantVersionTableId == null) {
            if (other.participantVersionTableId != null)
                return false;
        } else if (!participantVersionTableId.equals(other.participantVersionTableId))
            return false;
        if (projectId == null) {
            if (other.projectId != null)
                return false;
        } else if (!projectId.equals(other.projectId))
            return false;
        if (rawDataFolderId == null) {
            if (other.rawDataFolderId != null)
                return false;
        } else if (!rawDataFolderId.equals(other.rawDataFolderId))
            return false;
        if (storageLocationId == null) {
            if (other.storageLocationId != null)
                return false;
        } else if (!storageLocationId.equals(other.storageLocationId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Exporter3Configuration [createStudyNotificationTopicArn=" + createStudyNotificationTopicArn
                + ", dataAccessTeamId=" + dataAccessTeamId + ", participantVersionDemographicsTableId="
                + participantVersionDemographicsTableId + ", participantVersionDemographicsViewId="
                + participantVersionDemographicsViewId + ", participantVersionTableId=" + participantVersionTableId
                + ", projectId=" + projectId + ", rawDataFolderId=" + rawDataFolderId + ", storageLocationId="
                + storageLocationId + "]";
    }
}
