package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeConstants.APP_ACCESS_EXCEPTION_MSG;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.studies.App.APP_LIST_WRITER;
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
import org.sagebionetworks.bridge.models.studies.App;
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
public class AppController extends BaseController {

    static final StatusMessage CONSENT_EMAIL_VERIFIED_MSG = new StatusMessage("Consent notification email address verified.");

    static final StatusMessage RESEND_EMAIL_MSG = new StatusMessage("Resending verification email for consent notification email.");

    static final StatusMessage DELETED_MSG = new StatusMessage("App deleted.");

    private final Comparator<App> APP_COMPARATOR = new Comparator<App>() {
        public int compare(App app1, App app2) {
            return app1.getName().compareToIgnoreCase(app2.getName());
        }
    };

    private final Set<String> appWhitelist = Collections
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
    Set<String> getAppWhitelist() {
        return appWhitelist;
    }

    @GetMapping(path = {"/v1/apps/self", "/v3/studies/self"})
    public App getCurrentApp() {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);
        
        return appService.getApp(session.getAppId());
    }
    
    @PostMapping(path = {"/v1/apps/self", "/v3/studies/self"})
    public VersionHolder updateAppForDeveloperOrAdmin() {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);

        App appUpdate = parseJson(App.class);
        appUpdate.setIdentifier(session.getAppId());
        appUpdate = appService.updateApp(appUpdate, session.isInRole(ADMIN));
        return new VersionHolder(appUpdate.getVersion());
    }

    @PostMapping(path = {"/v1/apps/{appId}", "/v3/studies/{appId}"})
    public VersionHolder updateApp(@PathVariable String appId) {
        getAuthenticatedSession(SUPERADMIN);
        
        App appUpdate = parseJson(App.class);
        appUpdate.setIdentifier(appId);
        appUpdate = appService.updateApp(appUpdate, true);
        return new VersionHolder(appUpdate.getVersion());
    }

    @GetMapping(path = {"/v1/apps/{appId}", "/v3/studies/{appId}"})
    public App getApp(@PathVariable String appId) {
        getAuthenticatedSession(SUPERADMIN, WORKER);
        
        // since only admin and worker can call this method, we need to return all apps including deactivated ones
        return appService.getApp(appId, true);
    }

    // You can get a truncated view of apps with either format=summary or summary=true;
    // the latter allows us to make this a boolean flag in the Java client libraries.
    
    @GetMapping(path = {"/v1/apps", "/v3/studies"}, produces={APPLICATION_JSON_UTF8_VALUE})
    public String getAllApps(@RequestParam(required = false) String format,
            @RequestParam(required = false) String summary) throws Exception {        
        
        List<App> apps = appService.getApps();
        if ("summary".equals(format) || "true".equals(summary)) {
            // then only return active study as summary
            List<App> activeAppsSummary = apps.stream()
                    .filter(s -> s.isActive()).collect(Collectors.toList());
            Collections.sort(activeAppsSummary, APP_COMPARATOR);
            ResourceList<App> summaries = new ResourceList<App>(activeAppsSummary)
                    .withRequestParam("summary", true);
            return APP_LIST_WRITER.writeValueAsString(summaries);  
        }
        getAuthenticatedSession(SUPERADMIN);

        // otherwise, return all apps including deactivated ones
        return BridgeObjectMapper.get().writeValueAsString(
                new ResourceList<>(apps).withRequestParam("summary", false));
    }
    
    @GetMapping(path = { "/v1/apps/memberships", "/v3/studies/memberships" }, produces = {
            APPLICATION_JSON_UTF8_VALUE })
    public String getAppMemberships() throws Exception {   
        UserSession session = getAuthenticatedSession();
        
        if (session.getParticipant().getRoles().isEmpty()) {
            throw new UnauthorizedException(APP_ACCESS_EXCEPTION_MSG);
        }
        List<String> appIds = accountService.getAppIdsForUser(session.getParticipant().getSynapseUserId());
        
        Stream<App> stream = null;
        // In our current study permissions model, an admin in the API study is a 
        // "cross-study admin" and can see all apps and can switch between all apps, 
        // so check for this condition.
        if (session.isInRole(SUPERADMIN)) {
            stream = appService.getApps().stream()
                .filter(s -> s.isActive());
        } else {
            stream = appIds.stream()
                .map(id -> appService.getApp(id))
                .filter(s -> s.isActive() && appIds.contains(s.getIdentifier()));
        }
        List<App> apps = stream.sorted(APP_COMPARATOR).collect(toList());
        return APP_LIST_WRITER.writeValueAsString(new ResourceList<App>(apps));
    }

    @PostMapping(path = {"/v1/apps", "/v3/studies"})
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createApp() {
        getAuthenticatedSession(SUPERADMIN);

        App app = parseJson(App.class);
        app = appService.createApp(app);
        return new VersionHolder(app.getVersion());
    }

    @PostMapping(path = {"/v1/apps/init", "/v3/studies/init"})
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createAppAndUsers() throws SynapseException {
        getAuthenticatedSession(SUPERADMIN);

        StudyAndUsers studyAndUsers = parseJson(StudyAndUsers.class);
        App app = appService.createAppAndUsers(studyAndUsers);

        return new VersionHolder(app.getVersion());
    }

    @PostMapping(path = {"/v1/apps/self/synapseProject", "/v3/studies/self/synapseProject"})
    @ResponseStatus(HttpStatus.CREATED)
    public SynapseProjectIdTeamIdHolder createSynapse() throws SynapseException {
        // first get current app
        UserSession session = getAuthenticatedSession(DEVELOPER);
        App app = appService.getApp(session.getAppId());

        // then create project and team and grant admin permission to current user and exporter
        List<String> userIds = Arrays.asList(parseJson(String[].class));
        appService.createSynapseProjectTeam(ImmutableList.copyOf(userIds), app);

        return new SynapseProjectIdTeamIdHolder(app.getSynapseProjectId(), app.getSynapseDataAccessTeamId());
    }

    // since only admin can delete app, no need to check if return results should contain deactivated ones
    @DeleteMapping(path = {"/v1/apps/{appId}", "/v3/studies/{appId}"})
    public StatusMessage deleteApp(@PathVariable String appId,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        
        // Finally, you cannot delete your own app because it locks this user out of their session.
        // This is true of *all* users in the app, btw. There is an action in the BSM that iterates 
        // through all the participants in a study and signs them out one-by-one.
        if (session.getAppId().equals(appId)) {
            throw new UnauthorizedException("Admin cannot delete the app they are associated with.");
        }
        if (getAppWhitelist().contains(appId)) {
            throw new UnauthorizedException(appId + " is protected by whitelist.");
        }
        
        appService.deleteApp(appId, Boolean.valueOf(physical));

        return DELETED_MSG;
    }

    @GetMapping(path = {"/v1/apps/self/publicKey", "/v3/studies/self/publicKey"})
    public CmsPublicKey getAppPublicKeyAsPem() {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String pem = uploadCertificateService.getPublicKeyAsPem(session.getAppId());

        return new CmsPublicKey(pem);
    }

    @GetMapping(path = {"/v1/apps/self/emailStatus", "/v3/studies/self/emailStatus"})
    public EmailVerificationStatusHolder getEmailStatus() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        App app = appService.getApp(session.getAppId());

        EmailVerificationStatus status = emailVerificationService.getEmailStatus(app.getSupportEmail());
        return new EmailVerificationStatusHolder(status);
    }

    /** Resends the verification email for the current app's email. */
    @PostMapping(path = {"/v1/apps/self/emails/resendVerify", "/v3/studies/self/emails/resendVerify"})
    public StatusMessage resendVerifyEmail(@RequestParam(required = false) String type) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyEmailType parsedType = parseEmailType(type);
        appService.sendVerifyEmail(session.getAppId(), parsedType);
        return RESEND_EMAIL_MSG;
    }

    /**
     * Verifies the emails for the app. Since this comes in from an email with a token, you don't need to be
     * authenticated. The token itself knows what app this is for.
     */
    @PostMapping(path = {"/v1/apps/{appId}/emails/verify", "/v3/studies/{appId}/emails/verify"})
    public StatusMessage verifyEmail(@PathVariable String appId, @RequestParam(required = false) String token,
            @RequestParam(required = false) String type) {
        StudyEmailType parsedType = parseEmailType(type);
        appService.verifyEmail(appId, token, parsedType);
        return CONSENT_EMAIL_VERIFIED_MSG;
    }

    // Helper method to parse and validate the email type for app email verification workflow. We do verification
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

    @PostMapping(path = {"/v1/apps/self/verifyEmail", "/v3/studies/self/verifyEmail"})
    public EmailVerificationStatusHolder verifySenderEmail() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        App app = appService.getApp(session.getAppId());

        EmailVerificationStatus status = emailVerificationService.verifyEmailAddress(app.getSupportEmail());
        return new EmailVerificationStatusHolder(status);
    }

    @GetMapping(path = {"/v1/apps/self/uploads", "/v3/studies/self/uploads"})
    public ForwardCursorPagedResourceList<UploadView> getUploads(@RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime, @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String offsetKey) {
        UserSession session = getAuthenticatedSession(ADMIN);

        DateTime startTimeObj = BridgeUtils.getDateTimeOrDefault(startTime, null);
        DateTime endTimeObj = BridgeUtils.getDateTimeOrDefault(endTime, null);

        return uploadService.getAppUploads(session.getAppId(), startTimeObj, endTimeObj, pageSize,
                offsetKey);
    }

    /**
     * Another version of getUploads for workers to specify any app ID to get uploads
     */
    @GetMapping(path = {"/v1/apps/{appId}/uploads", "/v3/studies/{appId}/uploads"})
    public ForwardCursorPagedResourceList<UploadView> getUploadsForApp(@PathVariable String appId,
            @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String offsetKey) {
        getAuthenticatedSession(WORKER);
        
        // This won't happen because the route won't match, but tests look for a BadRequestException
        if (StringUtils.isBlank(appId)) {
            throw new BadRequestException("appId cannot be missing, null, or blank");
        }
        DateTime startTimeObj = BridgeUtils.getDateTimeOrDefault(startTime, null);
        DateTime endTimeObj = BridgeUtils.getDateTimeOrDefault(endTime, null);

        return uploadService.getAppUploads(appId, startTimeObj, endTimeObj, pageSize, offsetKey);
    }
}
