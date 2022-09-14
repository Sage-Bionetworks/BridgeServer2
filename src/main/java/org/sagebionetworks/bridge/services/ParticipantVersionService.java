package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantVersionDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.worker.Ex3ParticipantVersionRequest;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.time.DateUtils;

@Component
public class ParticipantVersionService {
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantVersionService.class);

    static final String WORKER_NAME_EX_3_PARTICIPANT_VERSION = "Ex3ParticipantVersionWorker";

    private AppService appService;
    private DemographicService demographicService;
    private BridgeConfig config;
    private ParticipantVersionDao participantVersionDao;
    private AmazonSQS sqsClient;

    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }

    @Autowired
    public final void setDemographicService(DemographicService demographicService) {
        this.demographicService = demographicService;
    }

    @Autowired
    public final void setConfig(BridgeConfig config) {
        this.config = config;
    }

    @Autowired
    public final void setParticipantVersionDao(ParticipantVersionDao participantVersionDao) {
        this.participantVersionDao = participantVersionDao;
    }

    @Autowired
    public final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    /** Creates a participant version from an account. */
    public void createParticipantVersionFromAccount(Account account) {
        String appId = account.getAppId();
        App app = appService.getApp(appId);
        if (!app.isExporter3Enabled()) {
            // If Exporter 3.0 isn't enabled, there's no point in creating a Participant Version.
            return;
        }

        if (!account.getRoles().isEmpty()) {
            // Accounts that have roles aren't research participants. Don't export them to Synapse.
            return;
        }
        if (account.getSharingScope() == SharingScope.NO_SHARING) {
            // no_sharing means we don't export this to Synapse, which means we can skip making a Participant Version.
            return;
        }
        if (account.getActiveEnrollments().isEmpty()) {
            // Participant has no active enrollments. Don't create a participant version.
            return;
        }

        ParticipantVersion participantVersion = makeParticipantVersionFromAccount(account);
        createParticipantVersion(participantVersion);
    }

    // Helper method which converts an Account into a ParticipantVersion.
    private ParticipantVersion makeParticipantVersionFromAccount(Account account) {
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
        participantVersion.setStudyMemberships(BridgeUtils.mapStudyMemberships(account));
        participantVersion.setTimeZone(account.getClientTimeZone());

        Map<String, Demographic> appDemographics = null;
        Optional<DemographicUser> appDemographicUser = demographicService.getDemographicUser(account.getAppId(), null,
                account.getId());
        if (appDemographicUser.isPresent()) {
            appDemographics = appDemographicUser.get().getDemographics();
        }
        participantVersion.setAppDemographics(appDemographics);
        Map<String, Map<String, Demographic>> studyDemographics = new HashMap<>();
        for (String studyId : participantVersion.getStudyMemberships().keySet()) {
            Map<String, Demographic> oneStudyDemographics = null;
            Optional<DemographicUser> studyDemographicUser = demographicService.getDemographicUser(account.getAppId(),
                    studyId, account.getId());
            if (studyDemographicUser.isPresent()) {
                oneStudyDemographics = studyDemographicUser.get().getDemographics();
            }
            studyDemographics.put(studyId, oneStudyDemographics);
        }
        participantVersion.setStudyDemographics(studyDemographics);

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
        Optional<ParticipantVersion> existingOpt = getLatestParticipantVersionForHealthCode(
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

        // Export.
        exportParticipantVersion(participantVersion.getAppId(), participantVersion.getHealthCode(),
                participantVersion.getParticipantVersion());
    }

    // Compares non-key attributes for participant versions. Returns true if they are the same, false if they are
    // different.
    // Package-scoped for unit tests.
    static boolean isIdenticalParticipantVersion(ParticipantVersion oldVersion, ParticipantVersion newVersion) {
        Map<String, Object> oldAttrMap = getParticipantVersionAttributes(oldVersion);
        Map<String, Object> newAttrMap = getParticipantVersionAttributes(newVersion);
        return oldAttrMap.equals(newAttrMap);
    }

    // This gets non-key attributes for the participant. This is mainly to test if the participant version has changed,
    // so we can avoid creating a new identical version. We use a map because it is easier to test than creating
    // a custom .equals() method.
    // Package-scoped for unit tests.
    static Map<String, Object> getParticipantVersionAttributes(ParticipantVersion participantVersion) {
        Map<String, Object> attrMap = new HashMap<>();
        attrMap.put("dataGroups", participantVersion.getDataGroups());
        attrMap.put("languages", participantVersion.getLanguages());
        attrMap.put("sharingScope", participantVersion.getSharingScope());
        attrMap.put("studyMemberships", participantVersion.getStudyMemberships());
        attrMap.put("timeZone", participantVersion.getTimeZone());
        attrMap.put("appDemographics", participantVersion.getAppDemographics());
        attrMap.put("studyDemographics", participantVersion.getStudyDemographics());
        return attrMap;
    }

    // This is separate because we might need a separate redrive process in the future.
    private void exportParticipantVersion(String appId, String healthCode, int versionNum) {
        // Create request.
        Ex3ParticipantVersionRequest participantVersionRequest = new Ex3ParticipantVersionRequest();
        participantVersionRequest.setAppId(appId);
        participantVersionRequest.setHealthCode(healthCode);
        participantVersionRequest.setParticipantVersion(versionNum);

        WorkerRequest workerRequest = new WorkerRequest();
        workerRequest.setService(WORKER_NAME_EX_3_PARTICIPANT_VERSION);
        workerRequest.setBody(participantVersionRequest);

        // Convert request to JSON.
        ObjectMapper objectMapper = BridgeObjectMapper.get();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(workerRequest);
        } catch (JsonProcessingException ex) {
            // This should never happen, but catch and re-throw for code hygiene.
            throw new BridgeServiceException("Error creating export participant version request for app " + appId +
                    " healthcode " + healthCode + " version " + versionNum, ex);
        }

        // Note: SqsInitializer runs after Spring, so we need to grab the queue URL dynamically.
        String workerQueueUrl = config.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL);

        // Sent to SQS.
        SendMessageResult sqsResult = sqsClient.sendMessage(workerQueueUrl, requestJson);
        LOG.info("Sent export participant version request for app " + appId + " healthCode " + healthCode +
                " version " + versionNum + "; received message ID=" + sqsResult.getMessageId());
    }

    /** Delete all participant versions for the given health code. This is called by integration tests. */
    public void deleteParticipantVersionsForHealthCode(String appId, String healthCode) {
        checkNotNull(appId);
        checkNotNull(healthCode);
        participantVersionDao.deleteParticipantVersionsForHealthCode(appId, healthCode);
    }

    /** Get all participant versions for health code. Returns an empty list if none exist. */
    public List<ParticipantVersion> getAllParticipantVersionsForHealthCode(String appId, String healthCode) {
        checkNotNull(appId);
        checkNotNull(healthCode);
        return participantVersionDao.getAllParticipantVersionsForHealthCode(appId, healthCode);
    }

    /** Retrieves the latest participant version for health code. */
    public Optional<ParticipantVersion> getLatestParticipantVersionForHealthCode(String appId, String healthCode) {
        checkNotNull(appId);
        checkNotNull(healthCode);
        return participantVersionDao.getLatestParticipantVersionForHealthCode(appId, healthCode);
    }

    /** Retrieves the participant version. Throws EntityNotFoundException if the participant version doesn't exist. */
    public ParticipantVersion getParticipantVersion(String appId, String healthCode, int participantVersion) {
        checkNotNull(appId);
        checkNotNull(healthCode);
        checkArgument(participantVersion > 0);
        return participantVersionDao.getParticipantVersion(appId, healthCode, participantVersion).orElseThrow(
                () -> new EntityNotFoundException(ParticipantVersion.class));
    }
}
