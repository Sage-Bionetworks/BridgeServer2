package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.SubstudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.substudies.Substudy;

@Component
public class AccountExternalIdMigrationService {

    AccountDao accountDao;
    
    SubstudyDao substudyDao;
    
    ParticipantService participantService;
    
    ExternalIdService externalIdService;
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Autowired
    final void setSubstudyDao(SubstudyDao substudyDao) {
        this.substudyDao = substudyDao;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    public String migrate(StudyIdentifier studyId, String userId, String substudyId) {
        AccountId accountId = AccountId.forId(studyId.getIdentifier(), userId);
        Account account = accountDao.getAccount(accountId);
        
        if (account.getExternalId() != null && account.getAccountSubstudies().isEmpty()) {
            if (substudyId == null) {
                List<Substudy> substudies = substudyDao.getSubstudies(studyId, false);
                if (substudies.isEmpty()) {
                    throw new BadRequestException("Cannot migrate account; no substudies are defined for study: " + studyId);
                }
                substudyId = substudies.get(0).getId();
            }
            
            Optional<ExternalIdentifier> opt = externalIdService.getExternalId(studyId, account.getExternalId());
            if (!opt.isPresent()) {
                ExternalIdentifier extId = ExternalIdentifier.create(studyId, account.getExternalId());
                extId.setSubstudyId(substudyId);
                externalIdService.createExternalId(extId, true);
            }
            
            ExternalIdentifier externalId = participantService.beginAssignExternalId(account, account.getExternalId());
            try {
                accountDao.updateAccount(account,
                        (modifiedAccount) -> externalIdService.commitAssignExternalId(externalId));
            } catch (Exception e) {
                if (externalId != null) {
                    externalIdService.unassignExternalId(account, externalId.getIdentifier());
                }
                throw e;
            }
            return substudyId;
        }
        return null;
    }
    
}
