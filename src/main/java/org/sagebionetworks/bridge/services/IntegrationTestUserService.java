package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.util.Map;

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

@Component
public class IntegrationTestUserService {

    private AuthenticationService authenticationService;
    private ParticipantService participantService;
    private AccountService accountService;
    private AdminAccountService adminAccountService;
    private ConsentService consentService;

    @Autowired
    final void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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
        
        IdentifierHolder identifier = null;

        try {
            if (!participant.getRoles().isEmpty() || participant.getOrgMembership() != null) {
                // I regret to inform you that you are actually creating an administrative account
                identifier = createAdminAccount(app.getIdentifier(), participant);
            } else {
                identifier = participantService.createParticipant(app, participant, false);
            }
            // We need to load the ID into the participant object because it is passed to several methods below
            participant = new StudyParticipant.Builder().copyOf(participant).withId(identifier.getIdentifier()).build();
            
            // We don't filter users by any of the filtering criteria in this test API.
            CriteriaContext context = new CriteriaContext.Builder().withUserId(identifier.getIdentifier())
                    .withAppId(app.getIdentifier()).build();
            
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
                accountService.deleteAccount(AccountId.forId(app.getIdentifier(), identifier.getIdentifier()));    
            }
            throw e;
        }
    }
    
    private IdentifierHolder createAdminAccount(String appId, StudyParticipant participant) {
        Account account = Account.create();
        account.setAppId(appId);
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
        
        account = adminAccountService.createAccount(appId, account);
        
        // force verification of this test account
        accountService.editAccount(AccountId.forId(account.getAppId(), account.getId()), (acct) -> {
            acct.setEmailVerified(TRUE);
            acct.setPhoneVerified(TRUE);
        });
        return new IdentifierHolder(account.getId());
    }
}
