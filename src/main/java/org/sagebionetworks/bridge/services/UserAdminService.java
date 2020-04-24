package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeUtils;
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
import org.sagebionetworks.bridge.models.studies.App;
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
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private ScheduledActivityService scheduledActivityService;
    private ActivityEventService activityEventService;
    private CacheProvider cacheProvider;
    private ExternalIdService externalIdService;
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
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    @Autowired
    final void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    @Autowired
    final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }
    
    /**
     * Create a user and optionally consent the user and/or sign the user in. If a specific subpopulation 
     * is not specified (and currently the API for this method does not allow it), than the method iterates 
     * through all subpopulations in the study and consents the user to all required consents. This should 
     * allow the user to make calls without receiving a 412 response. 
     * 
     * @param app
     *            the study of the target user
     * @param participant
     *            sign up information for the target user
     * @param subpopGuid
     *            the subpopulation to consent to (if null, it will use the default/study subpopulation).
     * @param signUserIn
     *            should the user be signed into Bridge after creation?
     * @param consentUser
     *            should the user be consented to the research?
     *
     * @return UserSession for the newly created user
     */
    public UserSession createUser(App app, StudyParticipant participant, SubpopulationGuid subpopGuid,
            boolean signUserIn, boolean consentUser) {
        checkNotNull(app, "Study cannot be null");
        checkNotNull(participant, "Participant cannot be null");
        
        // Validate study + email or phone. This is the minimum we need to create a functional account.
        SignIn signIn = new SignIn.Builder().withStudy(app.getIdentifier()).withEmail(participant.getEmail())
                .withPhone(participant.getPhone()).withPassword(participant.getPassword()).build();
        Validate.entityThrowingException(SignInValidator.MINIMAL, signIn);
        
        IdentifierHolder identifier = null;
        try {
            // This used to hard-code the admin role to allow assignment of roles; now it must actually be called by an 
            // admin user (previously this was only checked in the related controller method).
            identifier = participantService.createParticipant(app, participant, false);
            StudyParticipant updatedParticipant = participantService.getParticipant(app, identifier.getIdentifier(), false);
            
            // We don't filter users by any of these filtering criteria in the admin API.
            CriteriaContext context = new CriteriaContext.Builder()
                    .withUserId(identifier.getIdentifier())
                    .withAppId(app.getIdentifier()).build();
            
            if (consentUser) {
                String name = String.format("[Signature for %s]", updatedParticipant.getEmail());
                ConsentSignature signature = new ConsentSignature.Builder().withName(name)
                        .withBirthdate("1989-08-19").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
                
                if (subpopGuid != null) {
                    consentService.consentToResearch(app, subpopGuid, updatedParticipant, signature, NO_SHARING, false);
                } else {
                    Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
                    for (ConsentStatus consentStatus : statuses.values()) {
                        if (consentStatus.isRequired()) {
                            SubpopulationGuid guid = SubpopulationGuid.create(consentStatus.getSubpopulationGuid());
                            consentService.consentToResearch(app, guid, updatedParticipant, signature, NO_SHARING, false);
                        }
                    }
                }
            }
            if (signUserIn) {
                // We do ignore consent state here as our intention may be to create a user who is signed in but not
                // consented.
                try {
                    return authenticationService.signIn(app, context, signIn);    
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
     *      target user's study
     * @param id
     *      target user's ID
     */
    public void deleteUser(App app, String id) {
        checkNotNull(app);
        checkArgument(StringUtils.isNotBlank(id));
        
        AccountId accountId = AccountId.forId(app.getIdentifier(), id);
        Account account = accountService.getAccount(accountId);
        if (account != null) {
            // remove this first so if account is partially deleted, re-authenticating will pick
            // up accurate information about the state of the account (as we can recover it)
            cacheProvider.removeSessionByUserId(account.getId());
            requestInfoService.removeRequestInfo(account.getId());
            
            String healthCode = account.getHealthCode();
            healthDataService.deleteRecordsForHealthCode(healthCode);
            notificationsService.deleteAllRegistrations(app.getIdentifier(), healthCode);
            uploadService.deleteUploadsForHealthCode(healthCode);
            scheduledActivityService.deleteActivitiesForUser(healthCode);
            activityEventService.deleteActivityEvents(healthCode);
            for (String externalId : BridgeUtils.collectExternalIds(account)) {
                externalIdService.unassignExternalId(account, externalId);
            }
            // AccountSecret records and AccountsSubstudies records are are deleted on a 
            // cascading delete from Account
            accountService.deleteAccount(accountId);
        }
    }
}
