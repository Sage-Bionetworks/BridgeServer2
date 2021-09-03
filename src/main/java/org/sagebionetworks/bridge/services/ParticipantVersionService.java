package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ParticipantVersionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.time.DateUtils;

@Component
public class ParticipantVersionService {
    private ParticipantVersionDao participantVersionDao;
    private RequestInfoService requestInfoService;

    @Autowired
    public final void setParticipantVersionDao(ParticipantVersionDao participantVersionDao) {
        this.participantVersionDao = participantVersionDao;
    }

    @Autowired
    public final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }

    /** Creates a participant version from an account. */
    public void createParticipantVersionFromAccount(Account account) {
        ParticipantVersion participantVersion = makeParticipantVersionFromAccount(account);
        createParticipantVersion(participantVersion);
    }

    // Helper method which converts an Account into a ParticipantVersion.
    // Package-scoped for unit tests.
    ParticipantVersion makeParticipantVersionFromAccount(Account account) {
        checkNotNull(account);
        checkNotNull(account.getAppId());
        checkNotNull(account.getHealthCode());

        // These attributes map one-to-one.
        ParticipantVersion participantVersion = ParticipantVersion.create();
        participantVersion.setAppId(account.getAppId());
        participantVersion.setHealthCode(account.getHealthCode());
        participantVersion.setCreatedOn(account.getCreatedOn().getMillis());
        participantVersion.setDataGroups(account.getDataGroups());
        participantVersion.setLanguages(account.getLanguages());
        participantVersion.setSharingScope(account.getSharingScope());

        // Convert study memberships into a map.
        Map<String, String> studyMembershipMap = new HashMap<>();
        for (Enrollment enrollment : account.getActiveEnrollments()) {
            studyMembershipMap.put(enrollment.getStudyId(), enrollment.getExternalId());
        }
        participantVersion.setStudyMemberships(studyMembershipMap);

        // Account.timeZone only records the first time zone. We want the latest time zone. Get this from
        // RequestInfoService.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(account.getId());
        String timeZoneStr = DateUtils.timeZoneToOffsetString(requestInfo.getTimeZone());
        participantVersion.setTimeZone(timeZoneStr);

        return participantVersion;
    }

    /**
     * Creates a participant version from a StudyParticipant. This is called by ScheduledActivityController the
     * participant requests scheduled activities. This is when the participant's time zone is updated, and that time
     * zone is passed into this method.
     */
    public void createParticipantVersionFromParticipant(String appId, StudyParticipant participant,
            DateTimeZone timeZone) {
        ParticipantVersion participantVersion = makeParticipantVersionFromParticipant(appId, participant, timeZone);
        createParticipantVersion(participantVersion);
    }

    // Helper method which converts a StudyParticipant into a ParticipantVersion.
    // Package-scoped for unit tests.
    ParticipantVersion makeParticipantVersionFromParticipant(String appId, StudyParticipant participant,
            DateTimeZone timeZone) {
        checkNotNull(appId);
        checkNotNull(participant);
        checkNotNull(participant.getHealthCode());
        checkNotNull(timeZone);

        ParticipantVersion participantVersion = ParticipantVersion.create();
        participantVersion.setAppId(appId);
        participantVersion.setHealthCode(participant.getHealthCode());
        participantVersion.setCreatedOn(participant.getCreatedOn().getMillis());
        participantVersion.setDataGroups(participant.getDataGroups());
        participantVersion.setLanguages(participant.getLanguages());
        participantVersion.setSharingScope(participant.getSharingScope());
        participantVersion.setStudyMemberships(participant.getExternalIds());

        String timeZoneStr = DateUtils.timeZoneToOffsetString(timeZone);
        participantVersion.setTimeZone(timeZoneStr);

        return participantVersion;
    }

    // Helper method which creates a participant version. This method automatically increments the version number.
    // Package-scoped for unit tests.
    void createParticipantVersion(ParticipantVersion participantVersion) {
        checkNotNull(participantVersion);
        checkNotNull(participantVersion.getAppId());
        checkNotNull(participantVersion.getHealthCode());

        // Get the old version, so we increment the version number.
        long now = DateUtils.getCurrentMillisFromEpoch();
        Optional<ParticipantVersion> existingOpt = participantVersionDao.getLatestParticipantVersionForHealthCode(
                participantVersion.getAppId(), participantVersion.getHealthCode());
        if (existingOpt.isPresent()) {
            // Shortcut: If the participant version is unchanged, return early so we don't create a duplicate version.
            ParticipantVersion existing = existingOpt.get();
            if (isIdenticalParticipantVersion(existing, participantVersion)) {
                return;
            }

            participantVersion.setParticipantVersion(existing.getParticipantVersion() + 1);

            // createdOn can't be changed.
            participantVersion.setCreatedOn(existing.getCreatedOn());
        } else {
            // Initial version is 1.
            participantVersion.setParticipantVersion(1);

            // If createdOn is not specified, set it now.
            if (participantVersion.getCreatedOn() == 0) {
                participantVersion.setCreatedOn(now);
            }
        }

        // Update modifiedOn.
        participantVersion.setModifiedOn(now);

        // Create.
        participantVersionDao.createParticipantVersion(participantVersion);
    }

    // Compares non-key attributes for participant versions. Returns true if they are the same, false if they are
    // different.
    // Package-scoped for unit tests.
    boolean isIdenticalParticipantVersion(ParticipantVersion oldVersion, ParticipantVersion newVersion) {
        Map<String, Object> oldAttrMap = getParticipantVersionAttributes(oldVersion);
        Map<String, Object> newAttrMap = getParticipantVersionAttributes(newVersion);
        return oldAttrMap.equals(newAttrMap);
    }

    // This gets non-key attributes for the participant. This is mainly to test if the participant version has changed,
    // so we can avoid creating a new identical version. We use a map because it is easier to test than creating
    // a custom .equals() method.
    // Package-scoped for unit tests.
    Map<String, Object> getParticipantVersionAttributes(ParticipantVersion participantVersion) {
        Map<String, Object> attrMap = new HashMap<>();
        attrMap.put("dataGroups", participantVersion.getDataGroups());
        attrMap.put("languages", participantVersion.getLanguages());
        attrMap.put("sharingScope", participantVersion.getSharingScope());
        attrMap.put("studyMemberships", participantVersion.getStudyMemberships());
        attrMap.put("timeZone", participantVersion.getTimeZone());
        return attrMap;
    }

    /** Delete all participant versions for the given health code. This is called by integration tests. */
    public void deleteParticipantVersionsForHealthCode(String appId, String healthCode) {
        checkNotNull(appId);
        checkNotNull(healthCode);
        participantVersionDao.deleteParticipantVersionsForHealthCode(appId, healthCode);
    }

    /** Retrieves the participant version. Throws EntityNotFoundException if the participant version doesn't exist. */
    public ParticipantVersion getParticipantVersion(String appId, String healthCode, int participantVersion) {
        checkNotNull(healthCode);
        checkArgument(participantVersion > 0);
        return participantVersionDao.getParticipantVersion(appId, healthCode, participantVersion).orElseThrow(
                () -> new EntityNotFoundException(ParticipantVersion.class));
    }
}
