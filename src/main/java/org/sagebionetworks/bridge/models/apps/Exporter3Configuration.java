package org.sagebionetworks.bridge.models.apps;

import java.util.Objects;

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
        return Objects.hash(createStudyNotificationTopicArn, dataAccessTeamId, participantVersionDemographicsTableId,
                participantVersionDemographicsViewId, participantVersionTableId, projectId, rawDataFolderId,
                storageLocationId);
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
        return Objects.equals(createStudyNotificationTopicArn, other.createStudyNotificationTopicArn)
                && Objects.equals(dataAccessTeamId, other.dataAccessTeamId)
                && Objects.equals(participantVersionDemographicsTableId, other.participantVersionDemographicsTableId)
                && Objects.equals(participantVersionDemographicsViewId, other.participantVersionDemographicsViewId)
                && Objects.equals(participantVersionTableId, other.participantVersionTableId)
                && Objects.equals(projectId, other.projectId) && Objects.equals(rawDataFolderId, other.rawDataFolderId)
                && Objects.equals(storageLocationId, other.storageLocationId);
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
