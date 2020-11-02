package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.models.ResourceList.ID_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import java.util.Optional;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.apps.App;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for managing external IDs. These methods can be called whether or not strict validation of IDs is enabled. 
 * If it's enabled, reservation and assignment will work as expected, otherwise these silently do nothing. The external 
 * ID will be associated via the Enrollment collection, thus assignment of an external ID associates an account 
 * to a study (and removing an external ID removes that assignment).
 */
@Component
public class ExternalIdService {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    private ExternalIdDao externalIdDao;
    
    private AccountDao accountDao;

    @Autowired
    public final void setExternalIdDao(ExternalIdDao externalIdDao) {
        this.externalIdDao = externalIdDao;
    }
    
    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    public PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize) {
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return externalIdDao.getPagedExternalIds(appId, studyId, idFilter, offsetBy, pageSize)
                .withRequestParam(ID_FILTER, idFilter)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize);
    }

    public void deleteExternalIdPermanently(App app, ExternalIdentifier externalId) {
        checkNotNull(app);
        checkNotNull(externalId);
        
        AccountId accountId = AccountId.forExternalId(app.getIdentifier(), externalId.getIdentifier());
        Optional<Account> opt = accountDao.getAccount(accountId);
        if (!opt.isPresent() || BridgeUtils.filterForStudy(opt.get()) == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        externalIdDao.deleteExternalId(externalId);
    }
}
