package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for participant roster download.
 */

public class ParticipantRosterRequest {
    private final String password;
    private final String studyId;

    public ParticipantRosterRequest(@JsonProperty("password") String password, @JsonProperty("studyId") String studyId) {
        this.password = password;
        this.studyId = studyId;
    }

    public String getPassword() {
        return password;
    }

    public String getStudyId() {
        return studyId;
    }
}
