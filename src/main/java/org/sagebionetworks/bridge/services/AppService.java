package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.apps.MimeType.HTML;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.MembershipInvitation;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.util.ModelConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AppDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.apps.AppAndUsers;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.validators.AppAndUsersValidator;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.AppValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
@PropertySource("classpath:conf/app-defaults/sms-messages.properties")
public class AppService {
    private static final Logger LOG = LoggerFactory.getLogger(AppService.class);

    private static final String BASE_URL = BridgeConfigFactory.getConfig().get("webservices.url");
    static final String CONFIG_APP_WHITELIST = "study.whitelist";
    static final String CONFIG_KEY_SUPPORT_EMAIL_PLAIN = "support.email.plain";
    static final String CONFIG_KEY_SYNAPSE_TRACKING_VIEW = "synapse.tracking.view";
    static final String CONFIG_KEY_TEAM_BRIDGE_ADMIN = "team.bridge.admin";
    static final String CONFIG_KEY_TEAM_BRIDGE_STAFF = "team.bridge.staff";
    private static final String VERIFY_APP_EMAIL_URL = "%s/vse?appId=%s&token=%s&type=%s";
    static final int VERIFY_APP_EMAIL_EXPIRE_IN_SECONDS = 60*60*24;
    static final String EXPORTER_SYNAPSE_USER_ID = BridgeConfigFactory.getConfig().getExporterSynapseId(); // copy-paste from website
    static final String SYNAPSE_REGISTER_END_POINT = "https://www.synapse.org/#!NewAccount:";
    private static final String APP_PROPERTY = "App";
    private static final String TYPE_PROPERTY = "type";
    private static final String STUDY_EMAIL_VERIFICATION_URL = "studyEmailVerificationUrl";
    private static final String STUDY_EMAIL_VERIFICATION_EXPIRATION_PERIOD = "studyEmailVerificationExpirationPeriod";
    private static final String APP_EMAIL_VERIFICATION_URL = "appEmailVerificationUrl";
    private static final String APP_EMAIL_VERIFICATION_EXPIRATION_PERIOD = "appEmailVerificationExpirationPeriod";
    private static final String IDENTIFIER_PROPERTY = "identifier";
    public static final Set<ACCESS_TYPE> READ_DOWNLOAD_ACCESS = ImmutableSet.of(ACCESS_TYPE.READ, ACCESS_TYPE.DOWNLOAD);

    private Set<String> appWhitelist;
    private String bridgeSupportEmailPlain;
    private String bridgeAdminTeamId;
    private String bridgeStaffTeamId;
    private CompoundActivityDefinitionService compoundActivityDefinitionService;
    private SendMailService sendMailService;
    private UploadCertificateService uploadCertService;
    private AppDao appDao;
    private AppValidator validator;
    private AppAndUsersValidator appAndUsersValidator;
    private CacheProvider cacheProvider;
    private SubpopulationService subpopService;
    private NotificationTopicService topicService;
    private EmailVerificationService emailVerificationService;
    private SynapseClient synapseClient;
    private String synapseTrackingViewId;
    private ParticipantService participantService;
    private ExternalIdService externalIdService;
    private SubstudyService substudyService;
    private TemplateService templateService;
    private FileService fileService;

    // Not defaults, if you wish to change these, change in source. Not configurable per app
    private String appEmailVerificationTemplate;
    private String appEmailVerificationTemplateSubject;
    
    @Value("classpath:conf/templates/app-email-verification.txt")
    final void setAppEmailVerificationTemplate(org.springframework.core.io.Resource resource)
            throws IOException {
        this.appEmailVerificationTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/templates/app-email-verification-subject.txt")
    final void setAppEmailVerificationTemplateSubject(
            org.springframework.core.io.Resource resource) throws IOException {
        this.appEmailVerificationTemplateSubject = IOUtils.toString(resource.getInputStream(),
                StandardCharsets.UTF_8);
    }
    
    /** Bridge config. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeSupportEmailPlain = bridgeConfig.get(CONFIG_KEY_SUPPORT_EMAIL_PLAIN);
        this.bridgeAdminTeamId = bridgeConfig.get(CONFIG_KEY_TEAM_BRIDGE_ADMIN);
        this.bridgeStaffTeamId = bridgeConfig.get(CONFIG_KEY_TEAM_BRIDGE_STAFF);
        this.appWhitelist = Collections.unmodifiableSet(new HashSet<>(
                bridgeConfig.getPropertyAsList(CONFIG_APP_WHITELIST)));
        this.synapseTrackingViewId = bridgeConfig.get(CONFIG_KEY_SYNAPSE_TRACKING_VIEW);
    }

    /** Compound activity definition service, used to clean up deleted apps. This is set by Spring. */
    @Autowired
    final void setCompoundActivityDefinitionService(
            CompoundActivityDefinitionService compoundActivityDefinitionService) {
        this.compoundActivityDefinitionService = compoundActivityDefinitionService;
    }

    /** Send mail service, used to send the consent notification email verification email. */
    @Autowired
    public final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Resource(name="uploadCertificateService")
    final void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }
    @Autowired
    final void setValidator(AppValidator validator) {
        this.validator = validator;
    }
    @Autowired
    final void setAppAndUsersValidator(AppAndUsersValidator appAndUsersValidator) {
        this.appAndUsersValidator = appAndUsersValidator;
    }
    @Autowired
    final void setAppDao(AppDao appDao) {
        this.appDao = appDao;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    @Autowired
    final void setNotificationTopicService(NotificationTopicService topicService) {
        this.topicService = topicService;
    }
    @Autowired
    final void setEmailVerificationService(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }
    @Autowired
    @Qualifier("bridgePFSynapseClient")
    final void setSynapseClient(SynapseClient synapseClient) {
        this.synapseClient = synapseClient;
    }
    @Autowired
    final void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }
    @Autowired
    final void setFileService(FileService fileService) {
        this.fileService = fileService;
    }
    
    public App getApp(String identifier, boolean includeDeleted) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, IDENTIFIER_PROPERTY);

        App app = cacheProvider.getApp(identifier);
        if (app == null) {
            app = appDao.getApp(identifier);
            cacheProvider.setApp(app);
        }
        if (app != null) {
            // If it it exists and has been deactivated, and this call is not supposed to retrieve deactivated
            // apps, treat it as if it doesn't exist.
            if (!app.isActive() && !includeDeleted) {
                throw new EntityNotFoundException(App.class);
            }
            // Because these templates do not exist in all app, add the defaults where they are null
            if (app.getPasswordPolicy() == null) {
                app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
            }
        }
        return app;
    }

    // only return active app
    public App getApp(String identifier) {
        if (isBlank(identifier)) {
            throw new BadRequestException("identifier is required");
        }
        return getApp(identifier, false);
    }

    public List<App> getApps() {
        return appDao.getApps();
    }

    public App createAppAndUsers(AppAndUsers appAndUsers) throws SynapseException {
        checkNotNull(appAndUsers, Validate.CANNOT_BE_NULL, "app and users");
        
        App app = appAndUsers.getApp();
        StudyParticipantValidator val = new StudyParticipantValidator(externalIdService, substudyService, app, true);
        
        Errors errors = Validate.getErrorsFor(appAndUsers);
        
        // Validate AppAndUsers
        Validate.entity(appAndUsersValidator, errors, appAndUsers);
        Validate.throwException(errors, appAndUsers);
        
        // Validate each StudyParticipant object
        for (int i=0; i < appAndUsers.getUsers().size(); i++) {
            errors.pushNestedPath("users["+i+"]");
            StudyParticipant participant = appAndUsers.getUsers().get(i);
            Validate.entity(val, errors, participant);
            errors.popNestedPath();
        }
        Validate.throwException(errors, appAndUsers);

        // Create app
        app = createApp(appAndUsers.getApp());

        // Create users and send password reset email
        for (StudyParticipant user: appAndUsers.getUsers()) {
            IdentifierHolder identifierHolder = participantService.createParticipant(app, user, false);
            // send resetting password email as well
            participantService.requestResetPassword(app, identifierHolder.getIdentifier());
        }
        
        // Add admins and users to the Synapse project and access teams. All IDs have been validated.
        List<String> synapseUserIds = appAndUsers.getUsers().stream()
                .map(StudyParticipant::getSynapseUserId).collect(toList());
        createSynapseProjectTeam(appAndUsers.getAdminIds(), synapseUserIds, app);

        return app;
    }

    public App createApp(App app) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "app");
        if (app.getVersion() != null){
            throw new EntityAlreadyExistsException(App.class, "App has a version value; it may already exist",
                new ImmutableMap.Builder<String,Object>().put(IDENTIFIER_PROPERTY, app.getIdentifier()).build()); 
        }

        app.setActive(true);
        app.setConsentNotificationEmailVerified(false);
        app.setAppIdExcludedInExport(true);
        app.setVerifyChannelOnSignInEnabled(true);
        app.setEmailVerificationEnabled(true);
        app.getDataGroups().add(BridgeConstants.TEST_USER_GROUP);
        if (app.getPasswordPolicy() == null) {
            app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        }

        // If reauth isn't set on app creation, set it to true. We only do this at app creation and not on update,
        // because we don't want to suddenly be creating reauth tokens for old apps that don't use reauth.
        if (app.isReauthenticationEnabled() == null) {
            app.setReauthenticationEnabled(true);
        }

        // If validation strictness isn't set on app creation, set it to a reasonable default.
        if (app.getUploadValidationStrictness() == null) {
            app.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        }

        Validate.entityThrowingException(validator, app);

        if (appDao.doesIdentifierExist(app.getIdentifier())) {
            throw new EntityAlreadyExistsException(App.class, IDENTIFIER_PROPERTY, app.getIdentifier());
        }
        
        subpopService.createDefaultSubpopulation(app);
        
        Map<String,String> map = new HashMap<>();
        for (TemplateType type: TemplateType.values()) {
            String typeName = type.name().toLowerCase();
            Template template = Template.create();
            template.setName(BridgeUtils.templateTypeToLabel(type));
            template.setTemplateType(type);
            GuidVersionHolder keys = templateService.createTemplate(app, template);
            map.put(typeName, keys.getGuid());               
        }   
        app.setDefaultTemplates(map);

        // do not create certs for whitelisted apps (legacy apps)
        if (!appWhitelist.contains(app.getIdentifier())) {
            uploadCertService.createCmsKeyPair(app.getIdentifier());
        }

        app = appDao.createApp(app);
        cacheProvider.setApp(app);
        
        emailVerificationService.verifyEmailAddress(app.getSupportEmail());

        if (app.getConsentNotificationEmail() != null) {
            sendVerifyEmail(app, AppEmailType.CONSENT_NOTIFICATION);    
        }
        return app;
    }
    
    /**
     * Create a synapse project after creating a app. Administrator Synapse user IDs need to be verified, and 
     * no users are added to the data access team that will be created.
     */
    public App createSynapseProjectTeam(List<String> adminIds, App app) throws SynapseException {
        if (adminIds == null || adminIds.isEmpty()) {
            throw new BadRequestException("adminIds are required");
        }
        // then check if the user id exists
        for (String userId : adminIds) {
            try {
                synapseClient.getUserProfile(userId);
            } catch (SynapseNotFoundException e) {
                throw new BadRequestException("Synapse User Id: " + userId + " is invalid.");
            }
        }
        try {
            BridgeUtils.toSynapseFriendlyName(app.getName());    
        } catch(NullPointerException | IllegalArgumentException e) {
            throw new BadRequestException("App name is invalid Synapse name: " + app.getName());
        }
        return createSynapseProjectTeam(adminIds, ImmutableList.of(), app);
    }

    protected App createSynapseProjectTeam(List<String> synapseUserIds, List<String> userIds, App app) throws SynapseException {
        // first check if app already has project and team ids
        if (app.getSynapseDataAccessTeamId() != null){
            throw new EntityAlreadyExistsException(App.class, "App already has a team ID.",
                new ImmutableMap.Builder<String,Object>().put(IDENTIFIER_PROPERTY, app.getIdentifier())
                    .put("synapseDataAccessTeamId", app.getSynapseDataAccessTeamId()).build());
        }
        if (app.getSynapseProjectId() != null){
            throw new EntityAlreadyExistsException(App.class, "App already has a project ID.",
                new ImmutableMap.Builder<String,Object>().put(IDENTIFIER_PROPERTY, app.getIdentifier())
                .put("synapseProjectId", app.getSynapseProjectId()).build());
        }

        // Name in Synapse are globally unique, so we add a random token to the name to ensure it 
        // doesn't conflict with an existing name. Also, Synapse names can only contain a certain 
        // subset of characters. We've verified this name is acceptable for this transformation.
        String synapseName = BridgeUtils.toSynapseFriendlyName(app.getName());    
        String nameScopingToken = getNameScopingToken();

        // create synapse project and team
        Team team = new Team();
        team.setName(synapseName + " Access Team " + nameScopingToken);
        Team newTeam = synapseClient.createTeam(team);
        String newTeamId = newTeam.getId();

        Project project = new Project();
        project.setName(synapseName + " Project " + nameScopingToken);
        Project newProject = synapseClient.createEntity(project);
        String newProjectId = newProject.getId();

        // Add the exporter, bridge admin team, and individuals as admins
        AccessControlList projectACL = synapseClient.getACL(newProjectId);
        addAdminToACL(projectACL, EXPORTER_SYNAPSE_USER_ID); // add exporter as admin
        addAdminToACL(projectACL, bridgeAdminTeamId);
        for (String synapseUserId : synapseUserIds) {
            addAdminToACL(projectACL, synapseUserId);
        }
        // Add the data access team and bridge staff team as a read/download team
        addToACL(projectACL, bridgeStaffTeamId, READ_DOWNLOAD_ACCESS);
        addToACL(projectACL, newTeamId, READ_DOWNLOAD_ACCESS);
        synapseClient.updateACL(projectACL);

        addProjectToTrackingView(newProjectId);

        // send invitation to target user for joining new team and grant admin permission to that user.
        // Users added afterwards will have read/download rights through the access team.
        for (String synapseUserId : synapseUserIds) {
            MembershipInvitation teamMemberInvitation = new MembershipInvitation();
            teamMemberInvitation.setInviteeId(synapseUserId);
            teamMemberInvitation.setTeamId(newTeamId);
            synapseClient.createMembershipInvitation(teamMemberInvitation, null, null);
            synapseClient.setTeamMemberPermissions(newTeamId, synapseUserId, true);
        }
        // Add users as non-admin members of the team
        for (String userId : userIds) {
            MembershipInvitation teamMemberInvitation = new MembershipInvitation();
            teamMemberInvitation.setInviteeId(userId);
            teamMemberInvitation.setTeamId(newTeamId);
            synapseClient.createMembershipInvitation(teamMemberInvitation, null, null);
            synapseClient.setTeamMemberPermissions(newTeamId, userId, false);
        }

        // finally, update app
        app.setSynapseProjectId(newProjectId);
        app.setSynapseDataAccessTeamId(Long.parseLong(newTeamId));
        updateApp(app, false);

        return app;
    }

    // Package-scoped for unit tests.
    void addProjectToTrackingView(String projectId) {
        // Add the project to the tracking view, if it exists.
        if (StringUtils.isNotBlank(synapseTrackingViewId)) {
            try {
                EntityView view = synapseClient.getEntity(synapseTrackingViewId, EntityView.class);

                // For whatever reason, view.getScopes() doesn't include the "syn" prefix.
                view.getScopeIds().add(projectId.substring(3));
                synapseClient.putEntity(view);
            } catch (SynapseException ex) {
                LOG.error("Error adding new project " + projectId + " to tracking view " + synapseTrackingViewId +
                        ": " + ex.getMessage(), ex);
            }
        }
    }

    protected String getNameScopingToken() {
        return SecureTokenGenerator.NAME_SCOPE_INSTANCE.nextToken();
    }
    
    private void addAdminToACL(AccessControlList acl, String principalId) {
        addToACL(acl, principalId, ModelConstants.ENTITY_ADMIN_ACCESS_PERMISSIONS);
    }

    private void addToACL(AccessControlList acl, String principalId, Set<ACCESS_TYPE> accessTypes) {
        ResourceAccess resource = new ResourceAccess();
        resource.setPrincipalId(Long.parseLong(principalId));
        resource.setAccessType(accessTypes);
        acl.getResourceAccess().add(resource);
    }
    
    public App updateApp(App app, boolean isAdminUpdate) {
        checkNotNull(app, Validate.CANNOT_BE_NULL, "app");

        // These cannot be set through the API and will be null here, so they are set on update
        App originalApp = appDao.getApp(app.getIdentifier());
        
        checkViolationConstraints(originalApp, app);
        
        // A number of fields can only be set by an administrator. We set these to their existing values if the 
        // caller is not an admin.
        if (!isAdminUpdate) {
            // prevent non-admins update a deactivated app
            if (!originalApp.isActive()) {
                throw new EntityNotFoundException(App.class, "App '"+ app.getIdentifier() +"' not found.");
            }
            app.setHealthCodeExportEnabled(originalApp.isHealthCodeExportEnabled());
            app.setEmailVerificationEnabled(originalApp.isEmailVerificationEnabled());
            app.setExternalIdRequiredOnSignup(originalApp.isExternalIdRequiredOnSignup());
            app.setEmailSignInEnabled(originalApp.isEmailSignInEnabled());
            app.setPhoneSignInEnabled(originalApp.isPhoneSignInEnabled());
            app.setReauthenticationEnabled(originalApp.isReauthenticationEnabled());
            app.setAccountLimit(originalApp.getAccountLimit());
            app.setAppIdExcludedInExport(originalApp.isAppIdExcludedInExport());
            app.setVerifyChannelOnSignInEnabled(originalApp.isVerifyChannelOnSignInEnabled());
        }

        // prevent anyone changing active to false -- it should be done by deactivateApp() method
        if (originalApp.isActive() && !app.isActive()) {
            throw new BadRequestException("App cannot be deleted through an update.");
        }

        // With the introduction of the session verification email, apps won't have all the templates
        // that are normally required. So set it if someone tries to update a app, to a default value.
        if (app.getPasswordPolicy() == null) {
            app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        }

        Validate.entityThrowingException(validator, app);

        if (originalApp.isConsentNotificationEmailVerified() == null) {
            // Apps before the introduction of the consentNotificationEmailVerified flag have it set to null. For
            // backwards compatibility, treat this as "true". If these aren't actually verified, we'll handle it on a
            // case-by-case basis.
            app.setConsentNotificationEmailVerified(true);
        } else if (!originalApp.isConsentNotificationEmailVerified()) {
            // You can't use the updateApp() API to set consentNotificationEmailVerified from false to true.
            app.setConsentNotificationEmailVerified(false);
        }
        // This needs to happen before the app is updated.
        boolean consentHasChanged = !Objects.equals(originalApp.getConsentNotificationEmail(),
                app.getConsentNotificationEmail());
        if (consentHasChanged) {
            app.setConsentNotificationEmailVerified(false);
        }

        // Only admins can delete or modify upload metadata fields. Check this after validation, so we don't have to
        // deal with duplicates.
        // Anyone (admin or developer) can add or re-order fields.
        if (!isAdminUpdate) {
            checkUploadMetadataConstraints(originalApp, app);
        }

        App updatedApp = updateAndCacheApp(app);
        
        if (!originalApp.getSupportEmail().equals(app.getSupportEmail())) {
            emailVerificationService.verifyEmailAddress(app.getSupportEmail());
        }
        if (consentHasChanged && app.getConsentNotificationEmail() != null) {
            sendVerifyEmail(app, AppEmailType.CONSENT_NOTIFICATION);    
        }
        return updatedApp;
    }

    // Helper method to save the app to the DAO and also update the cache.
    private App updateAndCacheApp(App app) {
        // When the version is out of sync in the cache, then an exception is thrown and the app
        // is not updated in the cache. At least we can delete the app before this, so the next
        // time it should succeed. Have not figured out why they get out of sync.
        cacheProvider.removeApp(app.getIdentifier());
        App updatedApp = appDao.updateApp(app);
        cacheProvider.setApp(updatedApp);
        return updatedApp;
    }

    // Helper method to check if we deleted or modified an upload metadata fields. Only admins can delete or modify
    // upload metadata fields.
    private static void checkUploadMetadataConstraints(App oldApp, App newApp) {
        // Shortcut: if oldApp.uploadMetadataFieldDefinitions is empty, we can skip. Adding fields is always okay.
        if (oldApp.getUploadMetadataFieldDefinitions().isEmpty()) {
            return;
        }

        // Field defs are in lists because we care about the order, but for this computation, we want maps.
        Map<String, UploadFieldDefinition> oldFieldMap = Maps.uniqueIndex(oldApp.getUploadMetadataFieldDefinitions(),
                UploadFieldDefinition::getName);
        Map<String, UploadFieldDefinition> newFieldMap = Maps.uniqueIndex(newApp.getUploadMetadataFieldDefinitions(),
                UploadFieldDefinition::getName);

        // Determine if any fields were deleted (old minus new)
        Set<String> oldFieldNameSet = oldFieldMap.keySet();
        Set<String> newFieldNameSet = newFieldMap.keySet();
        Set<String> removedFieldNameSet = Sets.difference(oldFieldNameSet, newFieldNameSet);

        // Determine if any fields were changed. First find the overlap (intersection), then for each field name,
        // compare the old field with the new field.
        Set<String> overlapFieldNameSet = Sets.intersection(oldFieldNameSet, newFieldNameSet);
        Set<String> modifiedFieldNameSet = overlapFieldNameSet.stream()
                .filter(fieldName -> !Objects.equals(oldFieldMap.get(fieldName), newFieldMap.get(fieldName)))
                .collect(Collectors.toSet());

        // Use a TreeSet, so we can generate a message in a predictable order.
        Set<String> changedFieldSet = new TreeSet<>();
        changedFieldSet.addAll(removedFieldNameSet);
        changedFieldSet.addAll(modifiedFieldNameSet);

        if (!changedFieldSet.isEmpty()) {
            throw new UnauthorizedException("Non-admins cannot delete or modify upload metadata fields; " +
                    "affected fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(changedFieldSet));
        }
    }

    public void deleteApp(String identifier, boolean physical) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, IDENTIFIER_PROPERTY);

        if (appWhitelist.contains(identifier)) {
            throw new UnauthorizedException(identifier + " is protected by whitelist.");
        }

        // only admin can call this method, should contain deactivated ones.
        App existing = getApp(identifier, true);
        
        if (!physical) {
            // deactivate
            if (!existing.isActive()) {
                throw new BadRequestException("App '"+identifier+"' already deactivated.");
            }
            appDao.deactivateApp(existing.getIdentifier());
        } else {
            // actual delete
            appDao.deleteApp(existing);

            // delete app data
            templateService.deleteTemplatesForStudy(existing.getIdentifier());
            compoundActivityDefinitionService.deleteAllCompoundActivityDefinitionsInApp(
                    existing.getIdentifier());
            subpopService.deleteAllSubpopulations(existing.getIdentifier());
            topicService.deleteAllTopics(existing.getIdentifier());
            fileService.deleteAllStudyFiles(existing.getIdentifier());
        }

        cacheProvider.removeApp(identifier);
    }
    
    /**
     * The user cannot remove data groups or task identifiers. These may be referenced by many objects on the server,
     * and may be used by the client application. If any of these are missing on an update, throw a constraint
     * violation exception.
     */
    private void checkViolationConstraints(App originalApp, App app) {
        if (!app.getDataGroups().containsAll(originalApp.getDataGroups())) {
            throw new ConstraintViolationException.Builder()
                    .withEntityKey(IDENTIFIER_PROPERTY, app.getIdentifier()).withEntityKey(TYPE_PROPERTY, APP_PROPERTY)
                    .withMessage("Data groups cannot be deleted.").build();
        }
        if (!app.getTaskIdentifiers().containsAll(originalApp.getTaskIdentifiers())) {
            throw new ConstraintViolationException.Builder()
                    .withEntityKey(IDENTIFIER_PROPERTY, app.getIdentifier()).withEntityKey(TYPE_PROPERTY, APP_PROPERTY)
                    .withMessage("Task identifiers cannot be deleted.").build();
        }
        if (!app.getActivityEventKeys().containsAll(originalApp.getActivityEventKeys())) {
            throw new ConstraintViolationException.Builder()
                    .withEntityKey(IDENTIFIER_PROPERTY, app.getIdentifier()).withEntityKey(TYPE_PROPERTY, APP_PROPERTY)
                    .withMessage("Activity event keys cannot be deleted.").build();

        }
        if (app.getDefaultTemplates().keySet().size() != TemplateType.values().length) {
            throw new ConstraintViolationException.Builder()
                .withEntityKey(IDENTIFIER_PROPERTY, app.getIdentifier()).withEntityKey(TYPE_PROPERTY, APP_PROPERTY)
                .withMessage("Default templates cannot be deleted.").build();
        }
    }
    
    /** Sends the email verification email for the given app's email. */
    public void sendVerifyEmail(String appId, AppEmailType type) {
        App app = getApp(appId);
        sendVerifyEmail(app, type);
    }

    // Helper method to send the email verification email.
    private void sendVerifyEmail(App app, AppEmailType type) {
        checkNotNull(app);
        if (type == null) {
            throw new BadRequestException("Email type must be specified");
        }

        // Figure out which email we need to verify from type.
        String email;
        switch (type) {
            case CONSENT_NOTIFICATION:
                email = app.getConsentNotificationEmail();
                break;
            default:
                // Impossible code path, but put it in for future-proofing.
                throw new BadRequestException("Unrecognized email type \"" + type.toString() + "\"");
        }
        if (email == null) {
            throw new BadRequestException("Email not set for app");
        }

        // Generate and save token.
        String token = createTimeLimitedToken();
        saveVerification(token, new VerificationData(app.getIdentifier(), email));

        // Create and send verification email. Users cannot edit this template so there's no backwards
        // compatibility issues
        String appId = BridgeUtils.encodeURIComponent(app.getIdentifier());
        String shortUrl = String.format(VERIFY_APP_EMAIL_URL, BASE_URL, appId, token, type.toString().toLowerCase());
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject(appEmailVerificationTemplateSubject);
        revision.setDocumentContent(appEmailVerificationTemplate);
        revision.setMimeType(HTML);

        BasicEmailProvider provider = new BasicEmailProvider.Builder().withApp(app).withTemplateRevision(revision)
                .withOverrideSenderEmail(bridgeSupportEmailPlain).withRecipientEmail(email)
                .withToken(STUDY_EMAIL_VERIFICATION_URL, shortUrl)
                .withExpirationPeriod(STUDY_EMAIL_VERIFICATION_EXPIRATION_PERIOD, VERIFY_APP_EMAIL_EXPIRE_IN_SECONDS)
                .withToken(APP_EMAIL_VERIFICATION_URL, shortUrl)
                .withExpirationPeriod(APP_EMAIL_VERIFICATION_EXPIRATION_PERIOD, VERIFY_APP_EMAIL_EXPIRE_IN_SECONDS)
                .withType(EmailType.VERIFY_CONSENT_EMAIL)
                .build();
        sendMailService.sendEmail(provider);
    }

    /** Verifies the email with the given verification token. */
    public void verifyEmail(String appId, String token, AppEmailType type) {
        // Verify input.
        checkNotNull(appId);
        if (StringUtils.isBlank(token)) {
            throw new BadRequestException("Verification token must be specified");
        }
        if (type == null) {
            throw new BadRequestException("Email type must be specified");
        }

        // Check token against the cache.
        VerificationData data = restoreVerification(token);
        if (data == null) {
            throw new BadRequestException("Email verification token has expired (or already been used).");
        }

        // Figure out which email we need to verify from type.
        App app = getApp(appId);
        String email;
        switch (type) {
            case CONSENT_NOTIFICATION:
                email = app.getConsentNotificationEmail();
                break;
            default:
                // Impossible code path, but put it in for future-proofing.
                throw new BadRequestException("Unrecognized email type \"" + type.toString() + "\"");
        }

        // Make sure the app's current consent notification email matches the email saved in the verification data.
        // If the app's consent notification email is updated, the caller might still be using an older verification
        // email.
        if (!appId.equals(data.getAppId())) {
            throw new BadRequestException("Email verification token is for a different app.");
        }
        if (email == null || !email.equals(data.getEmail())) {
            throw new BadRequestException("Email verification token does not match consent notification email.");
        }

        // Use type to determine which email to verify.
        switch (type) {
            case CONSENT_NOTIFICATION:
                app.setConsentNotificationEmailVerified(true);
                break;
            default:
                // Impossible code path, but put it in for future-proofing.
                throw new BadRequestException("Unrecognized email type \"" + type.toString() + "\"");
        }

        // Update app.
        updateAndCacheApp(app);
    }

    // Creates a random token for consent notification email verification. Package-scoped so it can be mocked by unit
    // tests.
    String createTimeLimitedToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }

    // Helper method to save consent notification email verification data from the cache.
    private void saveVerification(String sptoken, VerificationData data) {
        checkArgument(isNotBlank(sptoken));
        checkNotNull(data);

        try {
            CacheKey cacheKey = CacheKey.verificationToken(sptoken);
            cacheProvider.setObject(cacheKey, BridgeObjectMapper.get().writeValueAsString(data),
                    VERIFY_APP_EMAIL_EXPIRE_IN_SECONDS);
        } catch (IOException e) {
            throw new BridgeServiceException(e);
        }
    }

    // Helper method to fetch consent notification email verification data from the cache.
    private VerificationData restoreVerification(String sptoken) {
        checkArgument(isNotBlank(sptoken));

        CacheKey cacheKey = CacheKey.verificationToken(sptoken);
        String json = cacheProvider.getObject(cacheKey, String.class);
        if (json != null) {
            try {
                cacheProvider.removeObject(cacheKey);
                return BridgeObjectMapper.get().readValue(json, VerificationData.class);
            } catch (IOException e) {
                throw new BridgeServiceException(e);
            }
        }
        return null;
    }

    // Verification data for consent notification email.
    private static class VerificationData {
        private final String appId;
        private final String email;

        @JsonCreator
        VerificationData(@JsonAlias("studyId") @JsonProperty("appId") String appId,
                @JsonProperty("email") String email) {
            checkArgument(isNotBlank(appId));
            checkArgument(isNotBlank(email));
            this.appId = appId;
            this.email = email;
        }

        // App ID that we want to verify email for.
        public String getAppId() {
            return appId;
        }

        // Email address that we want to verify.
        public String getEmail() {
            return email;
        }
    }
}
