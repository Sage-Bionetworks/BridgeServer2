package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.Enrollment;

@Component
public class HibernateExternalIdDao implements ExternalIdDao {
    
    private static final String BASE_QUERY = "from HibernateEnrollment as en "
            + "WHERE en.appId = :appId AND en.studyId = :studyId "
            + "AND en.externalId IS NOT NULL";
    private static final String FILTER_QUERY = "AND en.externalId LIKE :idFilter";
    private static final String ORDER_QUERY = "ORDER BY en.externalId";

    private HibernateHelper hibernateHelper;
    
    private AccountDao accountDao;

    @Resource(name = "accountHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    public PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(offsetBy);
        checkNotNull(pageSize);
        
        QueryBuilder query = new QueryBuilder();
        query.append(BASE_QUERY, "appId", appId, "studyId", studyId);
        if (StringUtils.isNotBlank(idFilter)) {
            query.append(FILTER_QUERY, "idFilter", idFilter + "%");
        }
        query.append(ORDER_QUERY);

        List<HibernateEnrollment> enrollments = hibernateHelper.queryGet("SELECT en " + query.getQuery(), 
                query.getParameters(), offsetBy, pageSize, HibernateEnrollment.class);

        List<ExternalIdentifierInfo> infos = enrollments.stream()
                .map(en -> new ExternalIdentifierInfo(en.getExternalId(), en.getStudyId(), true))
                .collect(Collectors.toList());

        int count = hibernateHelper.queryCount("SELECT count(en) " + query.getQuery(), query.getParameters());

        return new PagedResourceList<>(infos, count, true);
    }

    @Override
    public void deleteExternalId(ExternalIdentifier extId) {
        checkNotNull(extId);

        AccountId accountId = AccountId.forExternalId(extId.getAppId(), extId.getIdentifier());
        Account account = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        if (BridgeUtils.filterForStudy(account) == null) {
            throw new EntityNotFoundException(Account.class);
        }
        // If the caller used an external ID in a study the caller doesn't have access to,
        // the account retrieval would succeed but the filterForStudy call would then remove
        // the external Id. Externally we report this as an account not found exception.
        Enrollment enrollment = account.getEnrollments().stream()
                .filter(en -> extId.getIdentifier().equals(en.getExternalId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        enrollment.setExternalId(null);
        accountDao.updateAccount(account, null);
    }
}
