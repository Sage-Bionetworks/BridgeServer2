package org.sagebionetworks.bridge.models.worker;

/** Worker request to export a participant version for Exporter 3.0. */
public class Ex3ParticipantVersionRequest {
    private String appId;
    private String healthCode;
    private int participantVersion;

    /** App ID of the participant version to export. */
    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Health code of the participant version to export. */
    public String getHealthCode() {
        return healthCode;
    }

    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** Version number of the participant version to export. */
    public int getParticipantVersion() {
        return participantVersion;
    }

    public void setParticipantVersion(int participantVersion) {
        this.participantVersion = participantVersion;
    }
}
