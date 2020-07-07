
package org.sagebionetworks.bridge.hibernate;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.BridgeUtils.StudyAssociations;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.apps.App;

/** Hibernate implementation of Account Dao. */
@Component
public class HibernateAccountDao implements AccountDao {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);

    static final String ID_QUERY = "SELECT acct.id FROM HibernateAccount AS acct";
    
    static final String FULL_QUERY = "SELECT acct FROM HibernateAccount AS acct";
    
    static final String COUNT_QUERY = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct";
    
    private HibernateHelper hibernateHelper;

    /** This makes interfacing with Hibernate easier. */
    @Resource(name = "accountHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    // Provided to override in tests
    protected String generateGUID() {
        return BridgeUtils.generateGuid();
    }
    
    @Override
    public List<String> getAppIdForUser(String synapseUserId) {
        if (isBlank(synapseUserId)) {
            return ImmutableList.of();
        }
        QueryBuilder query = new QueryBuilder();
        query.append(
            "SELECT DISTINCT acct.appId FROM HibernateAccount AS acct WHERE synapseUserId = :synapseUserId",
            "synapseUserId", synapseUserId);
        return hibernateHelper.queryGet(query.getQuery(), query.getParameters(), null, null, String.class);
    }
    
    /** {@inheritDoc} */
    @Override
    public void createAccount(App app, Account account, Consumer<Account> afterPersistConsumer) {
        hibernateHelper.create(account, afterPersistConsumer);
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account, Consumer<Account> afterPersistConsumer) {
        hibernateHelper.update(account, afterPersistConsumer);
    }
    
    /** {@inheritDoc} */
    @Override
    public Optional<Account> getAccount(AccountId accountId) {
        HibernateAccount account = null;
        
        // The fastest retrieval can be done with the ID if it has been provided.
        AccountId unguarded = accountId.getUnguardedAccountId();
        if (unguarded.getId() != null) {
            account = hibernateHelper.getById(HibernateAccount.class, unguarded.getId());
            // Enforce the app membership of the accountId
            if (account == null || !account.getAppId().equals(accountId.getAppId())) {
                return Optional.empty();
            }
        } else {
            QueryBuilder builder = makeQuery(FULL_QUERY, unguarded.getAppId(), accountId, null, false);
            List<HibernateAccount> accountList = hibernateHelper.queryGet(
                    builder.getQuery(), builder.getParameters(), null, null, HibernateAccount.class);
            if (accountList.isEmpty()) {
                return Optional.empty();
            }
            account = accountList.get(0);
            if (accountList.size() > 1) {
                LOG.warn("Multiple accounts found email/phone query; example accountId=" + account.getId());
            }
        }
        if (validateHealthCode(account)) {
            Account updated = hibernateHelper.update(account, null);
            account.setVersion(updated.getVersion());
        }
        return Optional.of(account);
    }
    
    QueryBuilder makeQuery(String prefix, String appId, AccountId accountId, AccountSummarySearch search, boolean isCount) {
        RequestContext context = BridgeUtils.getRequestContext();
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(prefix);
        builder.append("LEFT JOIN acct.enrollments AS enrollment");
        builder.append("WITH acct.id = enrollment.accountId");
        builder.append("WHERE acct.appId = :appId", "appId", appId);
        
        if (accountId != null) {
            AccountId unguarded = accountId.getUnguardedAccountId();
            if (unguarded.getEmail() != null) {
                builder.append("AND acct.email=:email", "email", unguarded.getEmail());
            } else if (unguarded.getHealthCode() != null) {
                builder.append("AND acct.healthCode=:healthCode","healthCode", unguarded.getHealthCode());
            } else if (unguarded.getPhone() != null) {
                builder.append("AND acct.phone.number=:number AND acct.phone.regionCode=:regionCode",
                        "number", unguarded.getPhone().getNumber(),
                        "regionCode", unguarded.getPhone().getRegionCode());
            } else if (unguarded.getSynapseUserId() != null) {
                builder.append("AND acct.synapseUserId=:synapseUserId", "synapseUserId", unguarded.getSynapseUserId());
            } else {
                builder.append("AND enrollment.externalId=:externalId", "externalId", unguarded.getExternalId());
            }
        }
        if (search != null) {
            // Note: emailFilter can be any substring, not just prefix/suffix. Same with phone.
            if (StringUtils.isNotBlank(search.getEmailFilter())) {
                builder.append("AND acct.email LIKE :email", "email", "%"+search.getEmailFilter()+"%");
            }
            if (StringUtils.isNotBlank(search.getPhoneFilter())) {
                String phoneString = search.getPhoneFilter().replaceAll("\\D*", "");
                builder.append("AND acct.phone.number LIKE :number", "number", "%"+phoneString+"%");
            }
            // Note: start- and endTime are inclusive.            
            if (search.getStartTime() != null) {
                builder.append("AND acct.createdOn >= :startTime", "startTime", search.getStartTime());
            }
            if (search.getEndTime() != null) {
                builder.append("AND acct.createdOn <= :endTime", "endTime", search.getEndTime());
            }
            if (search.getLanguage() != null) {
                builder.append("AND :language IN ELEMENTS(acct.languages)", "language", search.getLanguage());
            }
            builder.adminOnly(search.isAdminOnly());
            builder.orgMembership(search.getOrgMembership());
            builder.dataGroups(search.getAllOfGroups(), "IN");
            builder.dataGroups(search.getNoneOfGroups(), "NOT IN");
        }
        Set<String> callerStudies = context.getCallerStudies();
        if (!callerStudies.isEmpty()) {
            builder.append("AND enrollment.studyId IN (:studies)", "studies", callerStudies);
        }
        if (!isCount) {
            builder.append("GROUP BY acct.id");        
        }
        return builder;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(String userId) {
        hibernateHelper.deleteById(HibernateAccount.class, userId);
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(String appId, AccountSummarySearch search) {
        // Getting the IDs and loading the records individually leads to N+1 queries (one id query and a 
        // query for each object), whereas querying for a constructor of a subset of columns leads to 
        // (N*Y)+1 queries as we must load each collection individually... Y=1 in the prior code to load
        // studies, and Y=2 once we add attributes. On the downside, this approach loads all 
        // HibernateAccount fields, like clientData, though it is not returned.
        QueryBuilder builder = makeQuery(ID_QUERY, appId, null, search, false);
        
        System.out.println(builder.getQuery());
        
        List<String> ids = hibernateHelper.queryGet(builder.getQuery(), builder.getParameters(),
                search.getOffsetBy(), search.getPageSize(), String.class);
        
        List<AccountSummary>accountSummaryList = ids.stream()
                .map(id -> hibernateHelper.getById(HibernateAccount.class, id))
                .map(this::unmarshallAccountSummary)
                .collect(Collectors.toList());

        // Get count of accounts.
        builder = makeQuery(COUNT_QUERY, appId, null, search, true);
        int count = hibernateHelper.queryCount(builder.getQuery(), builder.getParameters());
        
        // Package results and return.
        return new PagedResourceList<>(accountSummaryList, count)
                .withRequestParam(ResourceList.ADMIN_ONLY, search.isAdminOnly())
                .withRequestParam(ResourceList.ALL_OF_GROUPS, search.getAllOfGroups())
                .withRequestParam(ResourceList.EMAIL_FILTER, search.getEmailFilter())
                .withRequestParam(ResourceList.END_TIME, search.getEndTime())
                .withRequestParam(ResourceList.LANGUAGE, search.getLanguage())
                .withRequestParam(ResourceList.NONE_OF_GROUPS, search.getNoneOfGroups())
                .withRequestParam(ResourceList.OFFSET_BY, search.getOffsetBy())
                .withRequestParam(ResourceList.ORG_MEMBERSHIP, search.getOrgMembership())
                .withRequestParam(ResourceList.PAGE_SIZE, search.getPageSize())
                .withRequestParam(ResourceList.PHONE_FILTER, search.getPhoneFilter())
                .withRequestParam(ResourceList.START_TIME, search.getStartTime());
    }
    
    // Callers of AccountDao assume that an Account will always a health code and health ID. All accounts created
    // through the DAO will automatically have health code and ID populated, but accounts created in the DB directly
    // are left in a bad state. This method validates the health code mapping on a HibernateAccount and updates it as
    // is necessary.
    private boolean validateHealthCode(Account hibernateAccount) {
        if (StringUtils.isBlank(hibernateAccount.getHealthCode())) {
            hibernateAccount.setHealthCode(generateGUID());

            // We modified it. Update modifiedOn.
            DateTime modifiedOn = DateUtils.getCurrentDateTime();
            hibernateAccount.setModifiedOn(modifiedOn);
            return true;
        }
        return false;
    }

    // Helper method to unmarshall a HibernateAccount into an AccountSummary.
    // Package-scoped to facilitate unit tests.
    AccountSummary unmarshallAccountSummary(HibernateAccount acct) {
        AccountSummary.Builder builder = new AccountSummary.Builder();
        builder.withAppId(acct.getAppId());
        builder.withId(acct.getId());
        builder.withFirstName(acct.getFirstName());
        builder.withLastName(acct.getLastName());
        builder.withEmail(acct.getEmail());
        builder.withPhone(acct.getPhone());
        builder.withCreatedOn(acct.getCreatedOn());
        builder.withStatus(acct.getStatus());
        builder.withSynapseUserId(acct.getSynapseUserId());
        builder.withAttributes(acct.getAttributes());
        builder.withOrgMembership(acct.getOrgMembership());
        
        StudyAssociations assoc = BridgeUtils.studyAssociationsVisibleToCaller(null);
        if (acct.getId() != null) {
            assoc = BridgeUtils.studyAssociationsVisibleToCaller(acct.getEnrollments());
        }
        builder.withExternalIds(assoc.getExternalIdsVisibleToCaller());
        builder.withStudyIds(assoc.getStudyIdsVisibleToCaller());
        return builder.build();
    }
}
