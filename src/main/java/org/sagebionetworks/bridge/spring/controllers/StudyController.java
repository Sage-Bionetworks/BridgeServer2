package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_ACCESS_EXCEPTION_MSG;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.studies.Study.STUDY_LIST_WRITER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CmsPublicKey;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.studies.SynapseProjectIdTeamIdHolder;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;
import org.sagebionetworks.bridge.services.StudyEmailType;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UploadService;
import org.sagebionetworks.client.exceptions.SynapseException;

@CrossOrigin
@RestController
public class StudyController extends BaseController {

    static final StatusMessage CONSENT_EMAIL_VERIFIED_MSG = new StatusMessage("Consent notification email address verified.");

    static final StatusMessage RESEND_EMAIL_MSG = new StatusMessage("Resending verification email for consent notification email.");

    static final StatusMessage DELETED_MSG = new StatusMessage("Study deleted.");

    private final Comparator<Study> STUDY_COMPARATOR = new Comparator<Study>() {
        public int compare(Study study1, Study study2) {
            return study1.getName().compareToIgnoreCase(study2.getName());
        }
    };

    private final Set<String> studyWhitelist = Collections
            .unmodifiableSet(new HashSet<>(BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist")));

    private UploadCertificateService uploadCertificateService;

    private EmailVerificationService emailVerificationService;

    private UploadService uploadService;

    @Autowired
    final void setUploadCertificateService(UploadCertificateService uploadCertificateService) {
        this.uploadCertificateService = uploadCertificateService;
    }

    @Autowired
    final void setEmailVerificationService(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    
    // To enable mocking of values.
    Set<String> getStudyWhitelist() {
        return studyWhitelist;
    }

    @GetMapping("/v3/studies/self")
    public Study getCurrentStudy() {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        
        return studyService.getStudy(session.getAppId());
    }
    
    @PostMapping("/v3/studies/self")
    public VersionHolder updateStudyForDeveloperOrAdmin() {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);

        Study studyUpdate = parseJson(Study.class);
        studyUpdate.setIdentifier(session.getAppId());
        studyUpdate = studyService.updateStudy(studyUpdate, session.isInRole(ADMIN));
        return new VersionHolder(studyUpdate.getVersion());
    }

    @PostMapping("/v3/studies/{identifier}")
    public VersionHolder updateStudy(@PathVariable String identifier) {
        getAuthenticatedSession(SUPERADMIN);
        
        Study studyUpdate = parseJson(Study.class);
        studyUpdate.setIdentifier(identifier);
        studyUpdate = studyService.updateStudy(studyUpdate, true);
        return new VersionHolder(studyUpdate.getVersion());
    }

    @GetMapping("/v3/studies/{identifier}")
    public Study getStudy(@PathVariable String identifier) {
        getAuthenticatedSession(SUPERADMIN, WORKER);
        
        // since only admin and worker can call this method, we need to return all studies including deactivated ones
        return studyService.getStudy(identifier, true);
    }

    // You can get a truncated view of studies with either format=summary or summary=true;
    // the latter allows us to make this a boolean flag in the Java client libraries.
    
    @GetMapping(path="/v3/studies", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getAllStudies(@RequestParam(required = false) String format,
            @RequestParam(required = false) String summary) throws Exception {        
        
        List<Study> studies = studyService.getStudies();
        if ("summary".equals(format) || "true".equals(summary)) {
            // then only return active study as summary
            List<Study> activeStudiesSummary = studies.stream()
                    .filter(s -> s.isActive()).collect(Collectors.toList());
            Collections.sort(activeStudiesSummary, STUDY_COMPARATOR);
            ResourceList<Study> summaries = new ResourceList<Study>(activeStudiesSummary)
                    .withRequestParam("summary", true);
            return STUDY_LIST_WRITER.writeValueAsString(summaries);  
        }
        getAuthenticatedSession(SUPERADMIN);

        // otherwise, return all studies including deactivated ones
        return BridgeObjectMapper.get().writeValueAsString(
                new ResourceList<>(studies).withRequestParam("summary", false));
    }
    
    @GetMapping(path="/v3/studies/memberships", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getStudyMemberships() throws Exception {   
        UserSession session = getAuthenticatedSession();
        
        if (session.getParticipant().getRoles().isEmpty()) {
            throw new UnauthorizedException(STUDY_ACCESS_EXCEPTION_MSG);
        }
        List<String> studyIds = accountService.getStudyIdsForUser(session.getParticipant().getSynapseUserId());
        
        Stream<Study> stream = null;
        // In our current study permissions model, an admin in the API study is a 
        // "cross-study admin" and can see all studies and can switch between all studies, 
        // so check for this condition.
        if (session.isInRole(SUPERADMIN)) {
            stream = studyService.getStudies().stream()
                .filter(s -> s.isActive());
        } else {
            stream = studyIds.stream()
                .map(id -> studyService.getStudy(id))
                .filter(s -> s.isActive() && studyIds.contains(s.getIdentifier()));
        }
        List<Study> studies = stream.sorted(STUDY_COMPARATOR).collect(toList());
        return STUDY_LIST_WRITER.writeValueAsString(new ResourceList<Study>(studies));
    }

    @PostMapping("/v3/studies")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createStudy() {
        getAuthenticatedSession(SUPERADMIN);

        Study study = parseJson(Study.class);
        study = studyService.createStudy(study);
        return new VersionHolder(study.getVersion());
    }

    @PostMapping("/v3/studies/init")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createStudyAndUsers() throws SynapseException {
        getAuthenticatedSession(SUPERADMIN);

        StudyAndUsers studyAndUsers = parseJson(StudyAndUsers.class);
        Study study = studyService.createStudyAndUsers(studyAndUsers);

        return new VersionHolder(study.getVersion());
    }

    @PostMapping("/v3/studies/self/synapseProject")
    @ResponseStatus(HttpStatus.CREATED)
    public SynapseProjectIdTeamIdHolder createSynapse() throws SynapseException {
        // first get current study
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getAppId());

        // then create project and team and grant admin permission to current user and exporter
        List<String> userIds = Arrays.asList(parseJson(String[].class));
        studyService.createSynapseProjectTeam(ImmutableList.copyOf(userIds), study);

        return new SynapseProjectIdTeamIdHolder(study.getSynapseProjectId(), study.getSynapseDataAccessTeamId());
    }

    // since only admin can delete study, no need to check if return results should contain deactivated ones
    @DeleteMapping("/v3/studies/{identifier}")
    public StatusMessage deleteStudy(@PathVariable String identifier,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        // Finally, you cannot delete your own study because it locks this user out of their session.
        // This is true of *all* users in the study, btw. There is an action in the BSM that iterates 
        // through all the participants in a study and signs them out one-by-one.
        if (session.getAppId().equals(identifier)) {
            throw new UnauthorizedException("Admin cannot delete the study they are associated with.");
        }
        if (getStudyWhitelist().contains(identifier)) {
            throw new UnauthorizedException(identifier + " is protected by whitelist.");
        }
        
        studyService.deleteStudy(identifier, Boolean.valueOf(physical));

        return DELETED_MSG;
    }

    @GetMapping("/v3/studies/self/publicKey")
    public CmsPublicKey getStudyPublicKeyAsPem() {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String pem = uploadCertificateService.getPublicKeyAsPem(session.getAppId());

        return new CmsPublicKey(pem);
    }

    @GetMapping("/v3/studies/self/emailStatus")
    public EmailVerificationStatusHolder getEmailStatus() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getAppId());

        EmailVerificationStatus status = emailVerificationService.getEmailStatus(study.getSupportEmail());
        return new EmailVerificationStatusHolder(status);
    }

    /** Resends the verification email for the current study's email. */
    @PostMapping("/v3/studies/self/emails/resendVerify")
    public StatusMessage resendVerifyEmail(@RequestParam(required = false) String type) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyEmailType parsedType = parseEmailType(type);
        studyService.sendVerifyEmail(session.getAppId(), parsedType);
        return RESEND_EMAIL_MSG;
    }

    /**
     * Verifies the emails for the study. Since this comes in from an email with a token, you don't need to be
     * authenticated. The token itself knows what study this is for.
     */
    @PostMapping("/v3/studies/{identifier}/emails/verify")
    public StatusMessage verifyEmail(@PathVariable String identifier, @RequestParam(required = false) String token,
            @RequestParam(required = false) String type) {
        StudyEmailType parsedType = parseEmailType(type);
        studyService.verifyEmail(identifier, token, parsedType);
        return CONSENT_EMAIL_VERIFIED_MSG;
    }

    // Helper method to parse and validate the email type for study email verification workflow. We do verification
    // here so that the service can just deal with a clean enum.
    private static StudyEmailType parseEmailType(String typeStr) {
        if (StringUtils.isBlank(typeStr)) {
            throw new BadRequestException("Email type must be specified");
        }

        try {
            return StudyEmailType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unrecognized type \"" + typeStr + "\"");
        }
    }

    @PostMapping("/v3/studies/self/verifyEmail")
    public EmailVerificationStatusHolder verifySenderEmail() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getAppId());

        EmailVerificationStatus status = emailVerificationService.verifyEmailAddress(study.getSupportEmail());
        return new EmailVerificationStatusHolder(status);
    }

    @GetMapping("/v3/studies/self/uploads")
    public ForwardCursorPagedResourceList<UploadView> getUploads(@RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(ADMIN);

        DateTime startTimeObj = BridgeUtils.getDateTimeOrDefault(startTime, null);
        DateTime endTimeObj = BridgeUtils.getDateTimeOrDefault(endTime, null);

        return uploadService.getStudyUploads(session.getAppId(), startTimeObj, endTimeObj, pageSize,
                offsetKey);
    }

    /**
     * Another version of getUploads for workers to specify any study ID to get uploads
     */
    @GetMapping("/v3/studies/{identifier}/uploads")
    public ForwardCursorPagedResourceList<UploadView> getUploadsForStudy(@PathVariable String identifier,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String offsetKey) {
        getAuthenticatedSession(WORKER);
        
        // This won't happen because the route won't match, but tests look for a BadRequestException
        if (StringUtils.isBlank(identifier)) {
            throw new BadRequestException("studyId cannot be missing, null, or blank");
        }
        DateTime startTimeObj = BridgeUtils.getDateTimeOrDefault(startTime, null);
        DateTime endTimeObj = BridgeUtils.getDateTimeOrDefault(endTime, null);

        return uploadService.getStudyUploads(identifier, startTimeObj, endTimeObj, pageSize, offsetKey);
    }
}
