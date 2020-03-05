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
import com.google.common.collect.ImmutableMap;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

/** Hibernate implementation of Account Dao. */
@Component
public class HibernateAccountDao implements AccountDao {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);
    
    static final String SUMMARY_QUERY = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, "+
            "acct.firstName, acct.lastName, acct.email, acct.phone, acct.id, acct.status, " + 
            "acct.synapseUserId) FROM HibernateAccount AS acct";
            
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
    public List<String> getStudyIdsForUser(String synapseUserId) {
        if (isBlank(synapseUserId)) {
            return ImmutableList.of();
        }
        QueryBuilder query = new QueryBuilder();
        query.append(
            "SELECT DISTINCT acct.studyId FROM HibernateAccount AS acct WHERE synapseUserId = :synapseUserId",
            "synapseUserId", synapseUserId);
        return hibernateHelper.queryGet(query.getQuery(), query.getParameters(), null, null, String.class);
    }
    
    /** {@inheritDoc} */
    @Override
    public void createAccount(Study study, Account account, Consumer<Account> afterPersistConsumer) {
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
            // Enforce the study membership of the accountId
            if (account == null || !account.getStudyId().equals(accountId.getStudyId())) {
                return Optional.empty();
            }
        } else {
            QueryBuilder builder = makeQuery(FULL_QUERY, unguarded.getStudyId(), accountId, null, false);
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
    
    QueryBuilder makeQuery(String prefix, String studyId, AccountId accountId, AccountSummarySearch search, boolean isCount) {
        RequestContext context = BridgeUtils.getRequestContext();
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(prefix);
        builder.append("LEFT JOIN acct.accountSubstudies AS acctSubstudy");
        builder.append("WITH acct.id = acctSubstudy.accountId");
        builder.append("WHERE acct.studyId = :studyId", "studyId", studyId);

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
                builder.append("AND acctSubstudy.externalId=:externalId", "externalId", unguarded.getExternalId());
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
            builder.dataGroups(search.getAllOfGroups(), "IN");
            builder.dataGroups(search.getNoneOfGroups(), "NOT IN");
        }
        Set<String> callerSubstudies = context.getCallerSubstudies();
        if (!callerSubstudies.isEmpty()) {
            builder.append("AND acctSubstudy.substudyId IN (:substudies)", "substudies", callerSubstudies);
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
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
        QueryBuilder builder = makeQuery(SUMMARY_QUERY, study.getIdentifier(), null, search, false);

        // Get page of accounts.
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(builder.getQuery(), builder.getParameters(),
                search.getOffsetBy(), search.getPageSize(), HibernateAccount.class);
        List<AccountSummary> accountSummaryList = hibernateAccountList.stream()
                .map(this::unmarshallAccountSummary).collect(Collectors.toList());

        // Get count of accounts.
        builder = makeQuery(COUNT_QUERY, study.getIdentifier(), null, search, true);
        int count = hibernateHelper.queryCount(builder.getQuery(), builder.getParameters());
        
        // Package results and return.
        return new PagedResourceList<>(accountSummaryList, count)
                .withRequestParam(ResourceList.OFFSET_BY, search.getOffsetBy())
                .withRequestParam(ResourceList.PAGE_SIZE, search.getPageSize())
                .withRequestParam(ResourceList.EMAIL_FILTER, search.getEmailFilter())
                .withRequestParam(ResourceList.PHONE_FILTER, search.getPhoneFilter())
                .withRequestParam(ResourceList.START_TIME, search.getStartTime())
                .withRequestParam(ResourceList.END_TIME, search.getEndTime())
                .withRequestParam(ResourceList.LANGUAGE, search.getLanguage())
                .withRequestParam(ResourceList.ALL_OF_GROUPS, search.getAllOfGroups())
                .withRequestParam(ResourceList.NONE_OF_GROUPS, search.getNoneOfGroups());
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
    AccountSummary unmarshallAccountSummary(HibernateAccount hibernateAccount) {
        StudyIdentifier studyId = null;
        if (StringUtils.isNotBlank(hibernateAccount.getStudyId())) {
            studyId = new StudyIdentifierImpl(hibernateAccount.getStudyId());
        }
        // Hibernate will not load the collection of substudies once you use the constructor form of HQL 
        // to limit the data you retrieve from a table. May need to manually construct the objects to 
        // avoid this 1+N query.
        BridgeUtils.SubstudyAssociations assoc = null;
        if (hibernateAccount.getId() != null) {
            List<HibernateAccountSubstudy> accountSubstudies = hibernateHelper.queryGet(
                    "FROM HibernateAccountSubstudy WHERE accountId=:accountId",
                    ImmutableMap.of("accountId", hibernateAccount.getId()), null, null, HibernateAccountSubstudy.class);
            
            assoc = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies);
        } else {
            assoc = BridgeUtils.substudyAssociationsVisibleToCaller(null);
        }
        
        return new AccountSummary(hibernateAccount.getFirstName(), hibernateAccount.getLastName(),
                hibernateAccount.getEmail(), hibernateAccount.getSynapseUserId(), hibernateAccount.getPhone(),
                assoc.getExternalIdsVisibleToCaller(), hibernateAccount.getId(), hibernateAccount.getCreatedOn(),
                hibernateAccount.getStatus(), studyId, assoc.getSubstudyIdsVisibleToCaller());
    }
}
