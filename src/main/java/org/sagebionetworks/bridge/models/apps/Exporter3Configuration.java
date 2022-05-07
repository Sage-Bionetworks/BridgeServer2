package org.sagebionetworks.bridge.models.apps;

import java.util.Objects;

/** This class holds Exporter 3.0 configuration for a given app. */
public final class Exporter3Configuration {
    private Long dataAccessTeamId;
    private String participantVersionTableId;
    private String projectId;
    private String rawDataFolderId;
    private Long storageLocationId;
    private String wikiPageId;

    /** Helper method that returns true if all configuration attributes are specified. */
    public boolean isConfigured() {
        return dataAccessTeamId != null && participantVersionTableId != null && projectId != null &&
                rawDataFolderId != null && storageLocationId != null;
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

    public String getWikiPageId() {
        return wikiPageId;
    }

    public void setWikiPageId(String wikiPageId) {
        this.wikiPageId = wikiPageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Exporter3Configuration that = (Exporter3Configuration) o;
        return Objects.equals(dataAccessTeamId, that.dataAccessTeamId) &&
                Objects.equals(participantVersionTableId, that.participantVersionTableId) &&
                Objects.equals(projectId, that.projectId) &&
                Objects.equals(rawDataFolderId, that.rawDataFolderId) &&
                Objects.equals(storageLocationId, that.storageLocationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataAccessTeamId, participantVersionTableId, projectId, rawDataFolderId,
                storageLocationId);
    }

    @Override
    public String toString() {
        return String.format(
                "Exporter3Configuration [dataAccessTeamId=%s, participantVersionTableId=%s, projectId=%s, " +
                        "rawDataFolderId=%s, storageLocationId=%s]",
                dataAccessTeamId, projectId, participantVersionTableId, rawDataFolderId, storageLocationId);
    }
}
