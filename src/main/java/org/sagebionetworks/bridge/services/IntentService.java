package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.models.sms.SmsType.TRANSACTIONAL;

import java.util.List;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.validators.IntentToParticipateValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntentService {
    
    /** Hold on to the intent for 4 hours. */
    private static final int EXPIRATION_IN_SECONDS = 4 * 60 * 60;

    private AppService appService;
    
    private SubpopulationService subpopService;
    
    private ConsentService consentService;
    
    private CacheProvider cacheProvider;

    private AccountService accountService;
    
    private ParticipantService participantService;

    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    public void submitIntentToParticipate(IntentToParticipate intent) {
        Validate.entityThrowingException(IntentToParticipateValidator.INSTANCE, intent);
        
        // If the account exists, do nothing.
        AccountId accountId = null;
        if (intent.getPhone() != null) {
            accountId = AccountId.forPhone(intent.getAppId(), intent.getPhone());
        } else {
            accountId = AccountId.forEmail(intent.getAppId(), intent.getEmail());
        }
        Account account = accountService.getAccount(accountId).orElse(null);
        if (account != null) {
            return;
        }
        
        // validate app exists
        App app = appService.getApp(intent.getAppId());

        // validate subpopulation exists
        SubpopulationGuid guid = SubpopulationGuid.create(intent.getSubpopGuid());
        subpopService.getSubpopulation(app.getIdentifier(), guid);
        
        // validate it has not yet been submitted
        // the validator has ensured that phone or email, but not both, have been provided;
        CacheKey cacheKey = (intent.getPhone() == null) ?
                CacheKey.itp(guid, app.getIdentifier(), intent.getEmail()) :
                CacheKey.itp(guid, app.getIdentifier(), intent.getPhone());

        if (cacheProvider.getObject(cacheKey, IntentToParticipate.class) == null) {
            cacheProvider.setObject(cacheKey, intent, EXPIRATION_IN_SECONDS);
            
            // send an app store link to download the app, if we have something to send.
            // The URL being sent does not expire. We send with a transaction delivery type because
            // this is a critical step in onboarding through this workflow and message needs to be 
            // sent immediately after consenting.
            if (!app.getInstallLinks().isEmpty()) {
                participantService.sendInstallLinkMessage(
                        app, TRANSACTIONAL, null, intent.getEmail(), intent.getPhone(), intent.getOsName());
            }
        }
    }
    
    public boolean registerIntentToParticipate(App app, Account account) {
        Phone phone = account.getPhone();
        String email = account.getEmail();
        // Somehow, this is being called but the user has no phone number.
        if (phone == null && email == null) {
            return false;
        }
        boolean consentsUpdated = false;
        StudyParticipant participant = null;
        List<Subpopulation> subpops = subpopService.getSubpopulations(app.getIdentifier(), false);
        for (Subpopulation subpop : subpops) {
            CacheKey cacheKey = (phone == null) ?
                    CacheKey.itp(subpop.getGuid(), app.getIdentifier(), email) :
                    CacheKey.itp(subpop.getGuid(), app.getIdentifier(), phone);
            IntentToParticipate intent = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            if (intent != null) {
                if (participant == null) {
                    participant = participantService.getParticipant(app, account.getId(), true);
                }
                consentService.consentToResearch(app, subpop.getGuid(), participant, 
                        intent.getConsentSignature(), intent.getScope(), true);
                cacheProvider.removeObject(cacheKey);
                consentsUpdated = true;
            }
        }
        return consentsUpdated;
    }
}
