package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.models.ResourceList.ID_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for managing external IDs. These used to be maintained in a table separate from Accounts, but 
 * they are now a property of the enrollment of accounts in studies, so they defer to AccountService.
 */
@Component
public class ExternalIdService {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private AccountService accountService;

    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    public PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize) {
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return accountService.getPagedExternalIds(appId, studyId, idFilter, offsetBy, pageSize)
                .withRequestParam(ID_FILTER, idFilter)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize);
    }

    public void deleteExternalIdPermanently(App app, ExternalIdentifier externalId) {
        checkNotNull(app);
        checkNotNull(externalId);
        
        AccountId accountId = AccountId.forExternalId(externalId.getAppId(), externalId.getIdentifier());
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
                
        Enrollment enrollment = account.getEnrollments().stream()
                .filter(en -> externalId.getIdentifier().equals(en.getExternalId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        enrollment.setExternalId(null);
        accountService.updateAccount(account);
    }
}
