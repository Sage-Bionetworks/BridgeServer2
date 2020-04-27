package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.getRequestContext;
import static org.sagebionetworks.bridge.BridgeUtils.substudyAssociationsVisibleToCaller;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.CAN_BE_EDITED_BY;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITIES_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.BridgeUtils.SubstudyAssociations;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.AccountSummarySearchValidator;
import org.sagebionetworks.bridge.validators.IdentifierUpdateValidator;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class ParticipantService {
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantService.class);

    private AccountService accountService;

    private SmsService smsService;

    private SubpopulationService subpopService;

    private ConsentService consentService;

    private ExternalIdService externalIdService;
    
    private CacheProvider cacheProvider;
    
    private RequestInfoService requestInfoService;

    private ScheduledActivityDao activityDao;

    private UploadService uploadService;

    private NotificationsService notificationsService;

    private ScheduledActivityService scheduledActivityService;

    private ActivityEventService activityEventService;

    private AccountWorkflowService accountWorkflowService;
    
    private SubstudyService substudyService;

    @Autowired
    public final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    /** SMS Service, used to send text messages to participants. */
    @Autowired
    public void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    @Autowired
    final void setUserConsent(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setScheduledActivityDao(ScheduledActivityDao activityDao) {
        this.activityDao = activityDao;
    }

    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @Autowired
    final void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }

    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }

    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }
    
    @Autowired
    final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }
    
    /**
     * This is a researcher API to backfill SMS notification registrations for a user. We generally prefer the app
     * register notifications, but sometimes the work can't be done on time, so we want study developers to have the
     * option of backfilling these.
     */
    public void createSmsRegistration(App app, String userId) {
        checkNotNull(app);
        checkNotNull(userId);

        // Account must have a verified phone number.
        Account account = getAccountThrowingException(app.getIdentifier(), userId);
        if (!TRUE.equals(account.getPhoneVerified())) {
            throw new BadRequestException("Can't create SMS notification registration for user " + userId +
                    ": user has no verified phone number");
        }

        // We need the account's request info to build the criteria context.
        RequestInfo requestInfo = requestInfoService.getRequestInfo(userId);
        if (requestInfo == null) {
            throw new BadRequestException("Can't create SMS notification registration for user " + userId +
                    ": user has no request info");
        }
        Set<String> substudyIds = BridgeUtils.collectSubstudyIds(account);
        CriteriaContext criteriaContext = new CriteriaContext.Builder()
                .withAppId(app.getIdentifier())
                .withUserId(userId)
                .withHealthCode(account.getHealthCode())
                .withClientInfo(requestInfo.getClientInfo())
                .withLanguages(requestInfo.getLanguages())
                .withUserDataGroups(account.getDataGroups())
                .withUserSubstudyIds(substudyIds)
                .build();

        // Participant must be consented.
        StudyParticipant participant = getParticipant(app, account, true);
        if (!TRUE.equals(participant.isConsented())) {
            throw new BadRequestException("Can't create SMS notification registration for user " + userId +
                    ": user is not consented");
        }

        // Create registration.
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(account.getHealthCode());
        registration.setProtocol(NotificationProtocol.SMS);
        registration.setEndpoint(account.getPhone().getNumber());

        // Create registration.
        notificationsService.createRegistration(app.getIdentifier(), criteriaContext, registration);
    }

    public StudyParticipant getParticipant(App app, String userId, boolean includeHistory) {
        // This parse method correctly deserializes formats such as externalId:XXXXXXXX.
        AccountId accountId = BridgeUtils.parseAccountId(app.getIdentifier(), userId);
        Account account = getAccountThrowingException(accountId);
        return getParticipant(app, account, includeHistory);
    }
    
    public StudyParticipant getSelfParticipant(App app, CriteriaContext context, boolean includeHistory) {
        AccountId accountId = AccountId.forId(app.getIdentifier(),  context.getUserId());
        Account account = getAccountThrowingException(accountId); // already filters for substudy
        
        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        SubstudyAssociations assoc = BridgeUtils.substudyAssociationsVisibleToCaller(account.getAccountSubstudies());
        copyAccountToParticipant(builder, assoc, account);
        copyConsentStatusToParticipant(builder, account, context);
        if (includeHistory) {
            copyHistoryToParticipant(builder, account, context.getAppId());
        }
        return builder.build();
    }
    
    public StudyParticipant getParticipant(App app, Account account, boolean includeHistory) {
        if (account == null) {
            // This should never happen. However, it occasionally does happen, generally only during integration tests.
            // If a call is taking a long time for whatever reason, the call will timeout and the tests will delete the
            // account. If this happens in the middle of a call (such as give consent or update self participant),
            // we'll suddenly have no account here.
            //
            // We'll still want to log an error for this so we'll be aware when it happens. At the very least, we'll
            // have this comment and a marginally useful error message instead of a mysterious null pointer exception.
            //
            // See https://sagebionetworks.jira.com/browse/BRIDGE-1463 for more info.
            LOG.error("getParticipant() called with no account. Was the account deleted in the middle of the call?");
            throw new EntityNotFoundException(Account.class);
        }
        if (BridgeUtils.filterForSubstudy(account) == null) {
            throw new EntityNotFoundException(Account.class);
        }

        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        SubstudyAssociations assoc = substudyAssociationsVisibleToCaller(account.getAccountSubstudies());
        copyAccountToParticipant(builder, assoc, account);

        if (includeHistory) {
            copyHistoryToParticipant(builder, account, app.getIdentifier());
        }
        // Without requestInfo, we cannot reliably determine if the user is consented
        RequestInfo requestInfo = requestInfoService.getRequestInfo(account.getId());
        if (requestInfo != null) {
            CriteriaContext context = new CriteriaContext.Builder()
                .withAppId(app.getIdentifier())
                .withUserId(account.getId())
                .withHealthCode(account.getHealthCode())
                .withUserDataGroups(account.getDataGroups())
                .withUserSubstudyIds(assoc.getSubstudyIdsVisibleToCaller())
                .withClientInfo(requestInfo.getClientInfo())
                .withLanguages(requestInfo.getLanguages()).build();
            copyConsentStatusToParticipant(builder, account, context);
        }
        return builder.build();
    }
    
    private StudyParticipant.Builder copyAccountToParticipant(StudyParticipant.Builder builder, SubstudyAssociations assoc,
            Account account) {
        builder.withSharingScope(account.getSharingScope());
        builder.withNotifyByEmail(account.getNotifyByEmail());
        builder.withDataGroups(account.getDataGroups());
        builder.withLanguages(account.getLanguages());
        builder.withTimeZone(account.getTimeZone());
        builder.withFirstName(account.getFirstName());
        builder.withLastName(account.getLastName());
        builder.withEmail(account.getEmail());
        builder.withPhone(account.getPhone());
        builder.withEmailVerified(account.getEmailVerified());
        builder.withPhoneVerified(account.getPhoneVerified());
        builder.withStatus(account.getStatus());
        builder.withCreatedOn(account.getCreatedOn());
        builder.withRoles(account.getRoles());
        builder.withId(account.getId());
        builder.withHealthCode(account.getHealthCode());
        builder.withClientData(account.getClientData());
        builder.withAttributes(account.getAttributes());
        builder.withSubstudyIds(assoc.getSubstudyIdsVisibleToCaller());
        builder.withExternalIds(assoc.getExternalIdsVisibleToCaller());
        builder.withSynapseUserId(account.getSynapseUserId());
        return builder;
    }
    
    private StudyParticipant.Builder copyHistoryToParticipant(StudyParticipant.Builder builder, Account account, String studyId) {
        Map<String,List<UserConsentHistory>> consentHistories = Maps.newHashMap();
        // The history includes all subpopulations whether they match the user or not.
        List<Subpopulation> subpopulations = subpopService.getSubpopulations(studyId, false);
        for (Subpopulation subpop : subpopulations) {
            // always returns a list, even if empty
            List<UserConsentHistory> history = getUserConsentHistory(account, subpop.getGuid());
            consentHistories.put(subpop.getGuidString(), history);
        }
        builder.withConsentHistories(consentHistories);
        return builder;
    }
    
    private StudyParticipant.Builder copyConsentStatusToParticipant(StudyParticipant.Builder builder, Account account, CriteriaContext context) {
        Map<SubpopulationGuid, ConsentStatus> consentStatuses = consentService.getConsentStatuses(context, account);
        boolean isConsented = ConsentStatus.isUserConsented(consentStatuses);
        builder.withConsented(isConsented);
        return builder;
    }

    public PagedResourceList<AccountSummary> getPagedAccountSummaries(App app, AccountSummarySearch search) {
        checkNotNull(app);
        
        Validate.entityThrowingException(new AccountSummarySearchValidator(app.getDataGroups()), search);
        
        return accountService.getPagedAccountSummaries(app, search);
    }

    /**
     * Gets the timestamp representing when the participant started the study. Canonically, we define this as
     * activities_retrieved event time, then fall back to enrollment (for studies that don't use scheduling), then fall
     * back to account creation time (for studies that use neither scheduling nor consent).
     */
    public DateTime getStudyStartTime(AccountId accountId) {
        Account account = getAccountThrowingException(accountId);

        Map<String, DateTime> activityMap = activityEventService.getActivityEventMap(account.getAppId(), account.getHealthCode());
        DateTime activitiesRetrievedDateTime = activityMap.get(ACTIVITIES_RETRIEVED.name().toLowerCase());
        if (activitiesRetrievedDateTime != null) {
            return activitiesRetrievedDateTime;
        }

        DateTime enrollmentDateTime = activityMap.get(ENROLLMENT.name().toLowerCase());
        if (enrollmentDateTime != null) {
            return enrollmentDateTime;
        }

        return account.getCreatedOn();
    }

    public void signUserOut(App app, String userId, boolean deleteReauthToken) {
        checkNotNull(app);
        checkArgument(isNotBlank(userId));

        AccountId accountId = AccountId.forId(app.getIdentifier(), userId);
        Account account = getAccountThrowingException(accountId);

        if (deleteReauthToken) {
            accountService.deleteReauthToken(accountId);
        }
        
        cacheProvider.removeSessionByUserId(account.getId());
    }

    /**
     * Create a study participant. A password must be provided, even if it is added on behalf of a user before
     * triggering a reset password request.
     */
    public IdentifierHolder createParticipant(App app, StudyParticipant participant, boolean shouldSendVerification) {
        checkNotNull(app);
        checkNotNull(participant);
        
        if (app.getAccountLimit() > 0) {
            throwExceptionIfLimitMetOrExceeded(app);
        }
        
        StudyParticipantValidator validator = new StudyParticipantValidator(externalIdService, substudyService, app,
                true);
        Validate.entityThrowingException(validator, participant);
        
        // Set basic params from inputs.
        Account account = getAccount();
        account.setId(generateGUID());
        account.setAppId(app.getIdentifier());
        account.setEmail(participant.getEmail());
        account.setPhone(participant.getPhone());
        account.setEmailVerified(FALSE);
        account.setPhoneVerified(FALSE);
        account.setHealthCode(generateGUID());
        account.setStatus(UNVERIFIED);

        // Hash password if it has been supplied.
        if (participant.getPassword() != null) {
            try {
                PasswordAlgorithm passwordAlgorithm = DEFAULT_PASSWORD_ALGORITHM;
                String passwordHash = passwordAlgorithm.generateHash(participant.getPassword());
                account.setPasswordAlgorithm(passwordAlgorithm);
                account.setPasswordHash(passwordHash);
            } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
                throw new BridgeServiceException("Error creating password: " + ex.getMessage(), ex);
            }
        }
        
        final ExternalIdentifier externalId = beginAssignExternalId(account, participant.getExternalId());
        updateAccountAndRoles(app, account, participant, externalId, true);

        // enabled unless we need any kind of verification
        boolean sendEmailVerification = shouldSendVerification && app.isEmailVerificationEnabled();
        if (participant.getEmail() != null && !sendEmailVerification) {
            // not verifying, so consider it verified
            account.setEmailVerified(true); 
            account.setStatus(ENABLED);
        }
        if (participant.getPhone() != null && !shouldSendVerification) {
            // not verifying, so consider it verified
            account.setPhoneVerified(true); 
            account.setStatus(ENABLED);
        }
        // If external ID or Synapse ID only was provided, then the account will need to be enabled through 
        // use of the the AuthenticationService.generatePassword() pathway, or through authentication via 
        // Synapse
        if (shouldEnableCompleteExternalIdAccount(participant)) {
            account.setStatus(ENABLED);
        }
        if (shouldEnableCompleteSynapseUserIdAccount(participant)) {
            account.setStatus(ENABLED);
        }
        account.setSynapseUserId(participant.getSynapseUserId());
        
        // Set up the external ID object and the changes to the account, attempt to save the external ID 
        // within an account transaction, and roll back the account if the external ID save fails. If the 
        // account save fails, catch the exception and rollback the external ID save. 
        try {
            accountService.createAccount(app, account,
                    (modifiedAccount) -> externalIdService.commitAssignExternalId(externalId));
        } catch(Exception e) {
            if (externalId != null) {
                externalIdService.unassignExternalId(account, externalId.getIdentifier());    
            }
            throw e;
        }
        
        // send verify email
        if (sendEmailVerification && !app.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(app, account.getId(), account.getEmail());
        }

        // If you create an account with a phone number, this opts the phone number in to receiving SMS. We do this
        // _before_ phone verification / sign-in, because we need to opt the user in to SMS in order to send phone
        // verification / sign-in.
        Phone phone = account.getPhone();
        if (phone != null) {
            // Note that there is no object with both accountId and phone, so we need to pass them in separately.
            smsService.optInPhoneNumber(account.getId(), phone);
        }

        // send verify phone number
        if (shouldSendVerification && !app.isAutoVerificationPhoneSuppressed()) {
            accountWorkflowService.sendPhoneVerificationToken(app, account.getId(), phone);
        }
        return new IdentifierHolder(account.getId());
    }
    
    // Provided to override in tests
    protected Account getAccount() {
        return Account.create();
    }
    
    // Provided to override in tests
    protected String generateGUID() {
        return BridgeUtils.generateGuid();
    }
    
    private boolean shouldEnableCompleteExternalIdAccount(StudyParticipant participant) {
        return participant.getEmail() == null && participant.getPhone() == null && 
            participant.getExternalId() != null && participant.getPassword() != null;
    }
    
    private boolean shouldEnableCompleteSynapseUserIdAccount(StudyParticipant participant) {
        return participant.getEmail() == null && participant.getPhone() == null && 
            participant.getSynapseUserId() != null && participant.getPassword() == null;
    }

    public void updateParticipant(App app, StudyParticipant participant) {
        checkNotNull(app);
        checkNotNull(participant);

        StudyParticipantValidator validator = new StudyParticipantValidator(
                externalIdService, substudyService, app, false);
        Validate.entityThrowingException(validator, participant);
        
        Account account = getAccountThrowingException(app.getIdentifier(), participant.getId());
        
        final ExternalIdentifier externalId = beginAssignExternalId(account, participant.getExternalId());
        updateAccountAndRoles(app, account, participant, externalId, false);
        
        // Allow admin and worker accounts to toggle status; in particular, to disable/enable accounts.
        if (participant.getStatus() != null) {
            if (getRequestContext().isInRole(ImmutableSet.of(ADMIN, WORKER))) {
                account.setStatus(participant.getStatus());
            }
        }
        
        // Simple case, not trying to assign an external ID
        if (externalId == null) {
            accountService.updateAccount(account, null);
            return;
        }
        
        // Complex case: you are assigning an external ID. Set up the external ID object and the changes
        // to the account, attempt to save the external ID within an account transaction, and roll back 
        // the account if the external ID save fails. If the account save fails, catch the exception and 
        // rollback the external ID save. 
        try {
            accountService.updateAccount(account,
                    (modifiedAccount) -> externalIdService.commitAssignExternalId(externalId));
        } catch (Exception e) {
            externalIdService.unassignExternalId(account, externalId.getIdentifier());
            throw e;
        }
    }

    private void throwExceptionIfLimitMetOrExceeded(App app) {
        // It's sufficient to get minimum number of records, we're looking only at the total of all accounts
        PagedResourceList<AccountSummary> summaries = getPagedAccountSummaries(app, AccountSummarySearch.EMPTY_SEARCH);
        if (summaries.getTotal() >= app.getAccountLimit()) {
            throw new LimitExceededException(String.format(BridgeConstants.MAX_USERS_ERROR, app.getAccountLimit()));
        }
    }
    
    private void updateAccountAndRoles(App app, Account account, StudyParticipant participant,
            ExternalIdentifier externalId, boolean isNew) {
        account.setFirstName(participant.getFirstName());
        account.setLastName(participant.getLastName());
        account.setClientData(participant.getClientData());
        account.setSharingScope(participant.getSharingScope());
        account.setNotifyByEmail(participant.isNotifyByEmail());
        account.setDataGroups(participant.getDataGroups());
        account.setLanguages(participant.getLanguages());
        account.setMigrationVersion(MIGRATION_VERSION);
       
        // Sign out the user if you make alterations that will change the security state of 
        // the account. Otherwise very strange bugs can results.
        boolean clearCache = false;
        
        // Only allow the setting of substudies on new accounts. Note that while administrators can change this 
        // after the account is created, for admin accounts, it can create some very strange security behavior 
        // for that account if it is signed in, so we MUST destroy the session. 
        if (isNew || getRequestContext().isInRole(ADMIN)) {
            // Copy to prevent concurrent modification exceptions
            Set<AccountSubstudy> accountSubstudies = ImmutableSet.copyOf(account.getAccountSubstudies());
            
            // Remove substudy relationship if it's not desired and unassign external ID
            for (AccountSubstudy acctSubstudy : accountSubstudies) {
                if (!participant.getSubstudyIds().contains(acctSubstudy.getSubstudyId())) {
                    externalIdService.unassignExternalId(account, acctSubstudy.getExternalId());
                    account.getAccountSubstudies().remove(acctSubstudy);
                    clearCache = true;
                }
            }
            // Add substudy relationship
            Set<String> existingSubstudyIds = BridgeUtils.collectSubstudyIds(account);
            for (String substudyId : participant.getSubstudyIds()) {
                if (!existingSubstudyIds.contains(substudyId)) {
                    AccountSubstudy newSubstudy = AccountSubstudy.create(
                            account.getAppId(), substudyId, account.getId());
                    account.getAccountSubstudies().add(newSubstudy);
                    clearCache = true;
                }
            }
        }
        if (externalId != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getAppId(),
                    externalId.getSubstudyId(), account.getId());
            
            // If a substudy relationship exists without the external ID, remove it because
            // we're about to create it with an external ID
            if (account.getAccountSubstudies().contains(acctSubstudy)) {
                account.getAccountSubstudies().remove(acctSubstudy);
            }
            acctSubstudy.setExternalId(externalId.getIdentifier());
            account.getAccountSubstudies().add(acctSubstudy);
            clearCache = true;
        }
        // We have to clear the cache if we make changes that can alter the security profile of 
        // the account, otherwise very strange behavior can occur if that user is signed in with 
        // a stale session.
        if (!isNew && clearCache) {
            cacheProvider.removeSessionByUserId(account.getId());    
        }
        
        // Do not copy timezone (external ID field exists only to submit the value on create).
        
        for (String attribute : app.getUserProfileAttributes()) {
            String value = participant.getAttributes().get(attribute);
            account.getAttributes().put(attribute, value);
        }
        RequestContext requestContext = getRequestContext();
        if (requestContext.isAdministrator()) {
            updateRoles(requestContext, participant, account);
        }
        
        // If the caller is not in a substudy, any substudy tags are allowed. If there 
        // are any substudies assigned to the caller, then the participant must be assigned 
        // to one or more of those substudies, and only those substudies.
        Set<String> callerSubstudies = getRequestContext().getCallerSubstudies();
        if (!callerSubstudies.isEmpty()) {
            Set<String> accountSubstudies = BridgeUtils.collectSubstudyIds(account);
            if (accountSubstudies.isEmpty()) {
                throw new BadRequestException("Participant must be assigned to one or more of these substudies: "
                        + BridgeUtils.COMMA_JOINER.join(callerSubstudies));
            } else {
                for (String substudyId : accountSubstudies) {
                    if (!callerSubstudies.contains(substudyId)) {
                        throw new BadRequestException(substudyId + " is not a substudy of the caller");
                    }
                }
            }
        }
    }
    
    public void requestResetPassword(App app, String userId) {
        checkNotNull(app);
        checkArgument(isNotBlank(userId));

        // Don't throw an exception here, you'd be exposing that an email/phone number is in the system.
        AccountId accountId = AccountId.forId(app.getIdentifier(), userId);

        accountWorkflowService.requestResetPassword(app, true, accountId);
    }

    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(App app, String userId,
            String activityGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd, String offsetKey, int pageSize) {
        checkNotNull(app);
        checkArgument(isNotBlank(activityGuid));
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(app.getIdentifier(), userId);

        return scheduledActivityService.getActivityHistory(account.getHealthCode(), activityGuid, scheduledOnStart,
                scheduledOnEnd, offsetKey, pageSize);
    }
    
    public ForwardCursorPagedResourceList<ScheduledActivity> getActivityHistory(App app, String userId,
            ActivityType activityType, String referentGuid, DateTime scheduledOnStart, DateTime scheduledOnEnd,
            String offsetKey, int pageSize) {

        Account account = getAccountThrowingException(app.getIdentifier(), userId);

        return scheduledActivityService.getActivityHistory(account.getHealthCode(), activityType, referentGuid,
                scheduledOnStart, scheduledOnEnd, offsetKey, pageSize);
    }

    public void deleteActivities(App app, String userId) {
        checkNotNull(app);
        checkArgument(isNotBlank(userId));

        Account account = getAccountThrowingException(app.getIdentifier(), userId);

        activityDao.deleteActivitiesForUser(account.getHealthCode());
    }

    public void resendVerification(App app, ChannelType type, String userId) {
        checkNotNull(app);
        checkArgument(isNotBlank(userId));

        StudyParticipant participant = getParticipant(app, userId, false);
        if (type == ChannelType.EMAIL) { 
            if (participant.getEmail() != null) {
                AccountId accountId = AccountId.forEmail(app.getIdentifier(), participant.getEmail());
                accountWorkflowService.resendVerificationToken(type, accountId);
            }
        } else if (type == ChannelType.PHONE) {
            if (participant.getPhone() != null) {
                AccountId accountId = AccountId.forPhone(app.getIdentifier(), participant.getPhone());
                accountWorkflowService.resendVerificationToken(type, accountId);
            }
        } else {
            throw new UnsupportedOperationException("Channel type not implemented");
        }
    }

    public void withdrawFromStudy(App app, String userId, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(app);
        checkNotNull(userId);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        StudyParticipant participant = getParticipant(app, userId, false);

        consentService.withdrawFromStudy(app, participant, withdrawal, withdrewOn);
    }

    public void withdrawConsent(App app, String userId,
            SubpopulationGuid subpopGuid, Withdrawal withdrawal, long withdrewOn) {
        checkNotNull(app);
        checkNotNull(userId);
        checkNotNull(subpopGuid);
        checkNotNull(withdrawal);
        checkArgument(withdrewOn > 0);

        StudyParticipant participant = getParticipant(app, userId, false);
        CriteriaContext context = getCriteriaContextForParticipant(app, participant);

        consentService.withdrawConsent(app, subpopGuid, participant, context, withdrawal, withdrewOn);
    }
    
    public void resendConsentAgreement(App app, SubpopulationGuid subpopGuid, String userId) {
        checkNotNull(app);
        checkNotNull(subpopGuid);
        checkArgument(isNotBlank(userId));

        StudyParticipant participant = getParticipant(app, userId, false);
        consentService.resendConsentAgreement(app, subpopGuid, participant);
    }

    /**
     * Get a history of all consent records for a given subpopulation, whether user is withdrawn or not.
     */
    public List<UserConsentHistory> getUserConsentHistory(Account account, SubpopulationGuid subpopGuid) {
        return account.getConsentSignatureHistory(subpopGuid).stream().map(signature -> {
            Subpopulation subpop = subpopService.getSubpopulation(account.getAppId(), subpopGuid);
            boolean hasSignedActiveConsent = (signature.getConsentCreatedOn() == subpop.getPublishedConsentCreatedOn());

            return new UserConsentHistory.Builder()
                .withName(signature.getName())
                .withSubpopulationGuid(subpopGuid)
                .withBirthdate(signature.getBirthdate())
                .withImageData(signature.getImageData())
                .withImageMimeType(signature.getImageMimeType())
                .withSignedOn(signature.getSignedOn())
                .withHealthCode(account.getHealthCode())
                .withWithdrewOn(signature.getWithdrewOn())
                .withConsentCreatedOn(signature.getConsentCreatedOn())
                .withHasSignedActiveConsent(hasSignedActiveConsent).build();
        }).collect(BridgeCollectors.toImmutableList());
    }

    public ForwardCursorPagedResourceList<UploadView> getUploads(App app, String userId, DateTime startTime,
            DateTime endTime, Integer pageSize, String offsetKey) {
        checkNotNull(app);
        checkNotNull(userId);
        
        Account account = getAccountThrowingException(app.getIdentifier(), userId);

        return uploadService.getUploads(account.getHealthCode(), startTime, endTime, pageSize, offsetKey);
    }

    public List<NotificationRegistration> listRegistrations(App app, String userId) {
        checkNotNull(app);
        checkNotNull(userId);

        Account account = getAccountThrowingException(app.getIdentifier(), userId);

        return notificationsService.listRegistrations(account.getHealthCode());
    }

    public Set<String> sendNotification(App app, String userId, NotificationMessage message) {
        checkNotNull(app);
        checkNotNull(userId);
        checkNotNull(message);

        Account account = getAccountThrowingException(app.getIdentifier(), userId);

        return notificationsService.sendNotificationToUser(app.getIdentifier(), account.getHealthCode(), message);
    }

    /**
     * Send an SMS message to this user if they have a verified phone number. This message will be 
     * sent with AWS' non-critical, "Promotional" level of delivery that optimizes for cost.
     */
    public void sendSmsMessage(App app, String userId, SmsTemplate template) {
        checkNotNull(app);
        checkNotNull(userId);
        checkNotNull(template);
        
        if (StringUtils.isBlank(template.getMessage())) {
            throw new BadRequestException("Message is required");
        }
        Account account = getAccountThrowingException(app.getIdentifier(), userId);
        if (account.getPhone() == null || !TRUE.equals(account.getPhoneVerified())) {
            throw new BadRequestException("Account does not have a verified phone number");
        }
        Map<String,String> variables = BridgeUtils.studyTemplateVariables(app);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent(template.getMessage());
        revision.setMimeType(MimeType.TEXT);
        
        SmsMessageProvider.Builder builder = new SmsMessageProvider.Builder()
                .withPhone(account.getPhone())
                .withTemplateRevision(revision)
                .withPromotionType()
                .withStudy(app);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            builder.withToken(entry.getKey(), entry.getValue());
        }
        smsService.sendSmsMessage(userId, builder.build());
    }
    
    public List<ActivityEvent> getActivityEvents(App app, String userId) {
        Account account = getAccountThrowingException(app.getIdentifier(), userId);
        
        return activityEventService.getActivityEventList(app.getIdentifier(), account.getHealthCode());
    }
    
    /**
     * This method is only executed on the authenticated caller, not on behalf of any other person.
     */
    public StudyParticipant updateIdentifiers(App app, CriteriaContext context, IdentifierUpdate update) {
        checkNotNull(app);
        checkNotNull(context);
        checkNotNull(update);
        
        // Validate
        Validate.entityThrowingException(new IdentifierUpdateValidator(app, externalIdService), update);
        
        // Sign in
        Account account;
        // These throw exceptions for not found, disabled, and not yet verified.
        if (update.getSignIn().getReauthToken() != null) {
            account = accountService.reauthenticate(app, update.getSignIn());
        } else {
            account = accountService.authenticate(app, update.getSignIn());
        }
        // Verify the account matches the current caller
        if (!account.getId().equals(context.getUserId())) {
            throw new EntityNotFoundException(Account.class);
        }
        
        // reload account, or you will get an optimistic lock exception
        boolean sendEmailVerification = false;
        boolean accountUpdated = false;
        if (update.getPhoneUpdate() != null && account.getPhone() == null) {
            account.setPhone(update.getPhoneUpdate());
            account.setPhoneVerified(false);
            accountUpdated = true;
        }
        if (update.getEmailUpdate() != null && account.getEmail() == null) {
            account.setEmail(update.getEmailUpdate());
            account.setEmailVerified( !app.isEmailVerificationEnabled() );
            sendEmailVerification = true;
            accountUpdated = true;
        }
        if (update.getSynapseUserIdUpdate() != null && account.getSynapseUserId() == null) {
            account.setSynapseUserId(update.getSynapseUserIdUpdate());
            accountUpdated = true;
        }
        ExternalIdentifier externalId = beginAssignExternalId(account, update.getExternalIdUpdate());
        if (externalId != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getAppId(),
                    externalId.getSubstudyId(), account.getId());
            // Highly unlikely this was an admin account, but just in case
            if (account.getAccountSubstudies().contains(acctSubstudy)) {
                account.getAccountSubstudies().remove(acctSubstudy);
            }
            acctSubstudy.setExternalId(externalId.getIdentifier());
            account.getAccountSubstudies().add(acctSubstudy);
            try {
                accountService.updateAccount(account,
                        (modifiedAccount) -> externalIdService.commitAssignExternalId(externalId));
            } catch(Exception e) {
                externalIdService.unassignExternalId(account, externalId.getIdentifier());    
                throw e;
            }
            updateRequestContext(externalId);
        } else if (accountUpdated) {
            accountService.updateAccount(account, null);
        }
        if (sendEmailVerification && 
            app.isEmailVerificationEnabled() && 
            !app.isAutoVerificationEmailSuppressed()) {
            accountWorkflowService.sendEmailVerificationToken(app, account.getId(), account.getEmail());
        }
        // return updated StudyParticipant to update and return session
        return getParticipant(app, account.getId(), false);
    }
    
    protected ExternalIdentifier beginAssignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkNotNull(account.getAppId());
        checkNotNull(account.getHealthCode());
        
        Set<String> allExternalIds = BridgeUtils.collectExternalIds(account);
        if (externalId == null || allExternalIds.contains(externalId)) {
            return null;
        }
        
        ExternalIdentifier identifier = externalIdService.getExternalId(account.getAppId(), externalId).orElse(null);
        if (identifier == null) {
            return null;
        }
        if (identifier.getHealthCode() != null && !account.getHealthCode().equals(identifier.getHealthCode())) {
            throw new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", identifier.getIdentifier()); 
        }
        Set<String> substudies = BridgeUtils.collectSubstudyIds(account);
        if (!getRequestContext().isAdministrator() && substudies.contains(identifier.getSubstudyId())) {
            throw new ConstraintViolationException.Builder()
                .withMessage("Account already associated to substudy.")
                .withEntityKey("substudyId", identifier.getSubstudyId()).build();
        }
        identifier.setHealthCode(account.getHealthCode());
        return identifier;
    }


    /**
     * To see any new association to a substudy in the session that we return from the update identifiers call, 
     * we need to allow it in the permission structure of the call, which means we need to update the request 
     * context.
     */
    private void updateRequestContext(ExternalIdentifier externalId) {
        if (externalId.getSubstudyId() != null) {
            RequestContext currentContext = getRequestContext();
            
            Set<String> newSubstudies = new ImmutableSet.Builder<String>()
                    .addAll(currentContext.getCallerSubstudies())
                    .add(externalId.getSubstudyId()).build();
            
            RequestContext.Builder builder = currentContext.toBuilder();
            builder.withCallerSubstudies(newSubstudies);
            BridgeUtils.setRequestContext(builder.build());
        }
    }
     
    
    private CriteriaContext getCriteriaContextForParticipant(App app, StudyParticipant participant) {
        RequestInfo info = requestInfoService.getRequestInfo(participant.getId());
        ClientInfo clientInfo = (info == null) ? null : info.getClientInfo();
        
        return new CriteriaContext.Builder()
            .withAppId(app.getIdentifier())
            .withHealthCode(participant.getHealthCode())
            .withUserId(participant.getId())
            .withClientInfo(clientInfo)
            .withUserDataGroups(participant.getDataGroups())
            .withUserSubstudyIds(participant.getSubstudyIds())
            .withLanguages(participant.getLanguages()).build();
    }

    private boolean callerCanEditRole(RequestContext requestContext, Roles targetRole) {
        return requestContext.isInRole(CAN_BE_EDITED_BY.get(targetRole));
    }
    
    /**
     * For each role added, the caller must have the right to add the role. Then for every role currently assigned, we
     * check and if the caller doesn't have the right to remove that role, we'll add it back. Then we save those
     * results.
     */
    private void updateRoles(RequestContext requestContext, StudyParticipant participant, Account account) {
        Set<Roles> newRoleSet = Sets.newHashSet();
        // Caller can only add roles they have the rights to edit
        for (Roles role : participant.getRoles()) {
            if (callerCanEditRole(requestContext, role)) {
                newRoleSet.add(role);
            }
        }
        // Callers also can't remove roles they don't have the rights to edit
        for (Roles role : account.getRoles()) {
            if (!callerCanEditRole(requestContext, role)) {
                newRoleSet.add(role);
            }
        }
        account.setRoles(newRoleSet);
    }
    
    private Account getAccountThrowingException(String studyId, String id) {
        return getAccountThrowingException(AccountId.forId(studyId, id));
    }
    
    private Account getAccountThrowingException(AccountId accountId) {
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return account;
    }

}
