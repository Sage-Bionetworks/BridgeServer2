package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("userAdminService")
public class UserAdminService {

    private AuthenticationService authenticationService;
    private NotificationsService notificationsService;
    private ParticipantService participantService;
    private AccountService accountService;
    private AdminAccountService adminAccountService;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private HealthDataEx3Service healthDataEx3Service;
    private ScheduledActivityService scheduledActivityService;
    private ActivityEventService activityEventService;
    private CacheProvider cacheProvider;
    private UploadService uploadService;
    private RequestInfoService requestInfoService;

    @Autowired
    final void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /** Notifications service, used to clean up notification registrations when we delete users. */
    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    @Autowired
    final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
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
    final void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    @Autowired
    final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }
    @Autowired
    final void setAdminAccountService(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }
    
    /**
     * A method to create test users (only). Create a user and optionally consent
     * the user and/or sign the user in. If a specific subpopulation is not
     * specified (and currently the API for this method does not allow it), than the
     * method iterates through all subpopulations in the app and consents the user
     * to all required consents. This should allow the user to make calls without
     * receiving a 412 response.
     * 
     * @param appId       the app of the target user
     * @param participant sign up information for the target user
     * @param subpopGuid  the subpopulation to consent to (if null, it will use the
     *                    default/app subpopulation).
     * @param signUserIn  should the user be signed into Bridge after creation?
     * @param consentUser should the user be consented to the research?
     *
     * @return UserSession for the newly created user
     */
    public UserSession createUser(App app, StudyParticipant participant, SubpopulationGuid subpopGuid,
            boolean signUserIn, boolean consentUser) {
        checkNotNull(app, "App cannot be null");
        checkNotNull(participant, "Participant cannot be null");
        
        // Validate app + email or phone or external ID. This is the minimum we need to create a functional account.
        // Note that some tests add email/phone and external ID which we need to catch for sign in (where only one
        // credential is allowed).
        SignIn.Builder signInBuilder = new SignIn.Builder().withAppId(app.getIdentifier()).withEmail(participant.getEmail())
                .withPhone(participant.getPhone()).withPassword(participant.getPassword());
        if (participant.getEmail() == null && participant.getPhone() == null) {
            signInBuilder.withExternalId(participant.getExternalId());
        }
        Validate.entityThrowingException(SignInValidator.MINIMAL, signInBuilder.build());
        
        // Because this skips all sorts of workflow, it must be a test_user account.
        participant = new StudyParticipant.Builder().copyOf(participant)
                .withDataGroups(addToSet(participant.getDataGroups(), TEST_USER_GROUP)).build();
        IdentifierHolder identifier = null;

        try {
            if (!participant.getRoles().isEmpty() || participant.getOrgMembership() != null) {
                // I regret to inform you that you are actually creating an administrative account
                Account account = Account.create();
                account.setAppId(app.getIdentifier());
                account.setFirstName(participant.getFirstName());
                account.setLastName(participant.getLastName());
                account.setAttributes(participant.getAttributes());
                account.setEmail(participant.getEmail());
                account.setPhone(participant.getPhone());
                account.setPassword(participant.getPassword());
                account.setSynapseUserId(participant.getSynapseUserId());
                account.setStatus(participant.getStatus());
                account.setOrgMembership(participant.getOrgMembership());
                account.setRoles(participant.getRoles());
                account.setClientData(participant.getClientData());
                account.setTimeZone(participant.getTimeZone());
                account.setSharingScope(participant.getSharingScope());
                account.setNotifyByEmail(participant.isNotifyByEmail());
                account.setDataGroups(participant.getDataGroups());
                account.setLanguages(participant.getLanguages());
                account.setClientTimeZone(participant.getClientTimeZone());
                account.setNote(participant.getNote());
                
                account = adminAccountService.createAccount(app.getIdentifier(), account);
                
                identifier = new IdentifierHolder(account.getId());
                participant = new StudyParticipant.Builder().copyOf(participant)
                        .withId(account.getId()).build();
                // force verification of of this test account
                accountService.editAccount(AccountId.forId(account.getAppId(), account.getId()), (acct) -> {
                    acct.setEmailVerified(TRUE);
                    acct.setPhoneVerified(TRUE);
                });
            } else {
                identifier = participantService.createParticipant(app, participant, false);
                participant = participantService.getParticipant(app, identifier.getIdentifier(), false);
            }
            
            // We don't filter users by any of these filtering criteria in the admin API.
            CriteriaContext context = new CriteriaContext.Builder()
                    .withUserId(identifier.getIdentifier()).withAppId(app.getIdentifier()).build();
            if (consentUser) {
                String name = String.format("[Signature for %s]", participant.getEmail());
                ConsentSignature signature = new ConsentSignature.Builder().withName(name)
                        .withBirthdate("1989-08-19").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
                
                if (subpopGuid != null) {
                    consentService.consentToResearch(app, subpopGuid, participant, signature, NO_SHARING, false);
                } else {
                    Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
                    for (ConsentStatus consentStatus : statuses.values()) {
                        if (consentStatus.isRequired()) {
                            SubpopulationGuid guid = SubpopulationGuid.create(consentStatus.getSubpopulationGuid());
                            consentService.consentToResearch(app, guid, participant, signature, NO_SHARING, false);
                        }
                    }
                }
            }
            if (signUserIn) {
                // We do ignore consent state here as our intention may be to create a user who is signed in but not
                // consented.
                try {
                    return authenticationService.signIn(app, context, signInBuilder.build());    
                } catch(ConsentRequiredException e) {
                    return e.getUserSession();
                }
                
            }
            // Return a session *without* signing in because we have 3 sign in pathways that we want to test. In this case
            // we're creating a session but not authenticating you which is only a thing that's useful for tests.
            UserSession session = authenticationService.getSession(app, context);
            session.setAuthenticated(false);
            return session;
        } catch(RuntimeException e) {
            // Created the account, but failed to process the account properly. To avoid leaving behind a bunch of test
            // accounts, delete this account.
            if (identifier != null) {
                deleteUser(app, identifier.getIdentifier());    
            }
            throw e;
        }
    }

    /**
     * Delete the target user.
     *
     * @param app
     *      target user's app
     * @param id
     *      target user's ID
     */
    public void deleteUser(App app, String id) {
        checkNotNull(app);
        checkArgument(StringUtils.isNotBlank(id));
        
        AccountId accountId = AccountId.forId(app.getIdentifier(), id);
        Account account = accountService.getAccount(accountId).orElse(null);
        if (account != null) {
            // remove this first so if account is partially deleted, re-authenticating will pick
            // up accurate information about the state of the account (as we can recover it)
            cacheProvider.removeSessionByUserId(account.getId());
            requestInfoService.removeRequestInfo(account.getId());
            
            String healthCode = account.getHealthCode();
            healthDataService.deleteRecordsForHealthCode(healthCode);
            healthDataEx3Service.deleteRecordsForHealthCode(healthCode);
            notificationsService.deleteAllRegistrations(app.getIdentifier(), healthCode);
            uploadService.deleteUploadsForHealthCode(healthCode);
            scheduledActivityService.deleteActivitiesForUser(healthCode);
            activityEventService.deleteActivityEvents(app.getIdentifier(), healthCode);
            // AccountSecret records and Enrollment records are are deleted on a 
            // cascading delete from Account
            accountService.deleteAccount(accountId);
        }
    }
}
