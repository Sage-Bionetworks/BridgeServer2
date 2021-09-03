package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;

public interface ParticipantVersionDao {
    /** Creates a participant version. */
    void createParticipantVersion(ParticipantVersion participantVersion);

    /** Delete all participant versions for the given app and health code. This is called by integration tests. */
    void deleteParticipantVersionsForHealthCode(String appId, String healthCode);

    /** Retrieves the participant version. */
    Optional<ParticipantVersion> getParticipantVersion(String appId, String healthCode, int participantVersion);

    /** Retrieves the latest participant version for health code. */
    Optional<ParticipantVersion> getLatestParticipantVersionForHealthCode(String appId, String healthCode);
}
