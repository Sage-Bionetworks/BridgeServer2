package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.ResourceList.ADMIN_ONLY;
import static org.sagebionetworks.bridge.models.ResourceList.ALL_OF_GROUPS;
import static org.sagebionetworks.bridge.models.ResourceList.ATTRIBUTE_KEY;
import static org.sagebionetworks.bridge.models.ResourceList.ATTRIBUTE_VALUE_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.EMAIL_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.ENROLLED_IN_STUDY_ID;
import static org.sagebionetworks.bridge.models.ResourceList.ENROLLMENT;
import static org.sagebionetworks.bridge.models.ResourceList.EXTERNAL_ID_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.IN_USE;
import static org.sagebionetworks.bridge.models.ResourceList.LANGUAGE;
import static org.sagebionetworks.bridge.models.ResourceList.NONE_OF_GROUPS;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.ORG_MEMBERSHIP;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.PHONE_FILTER;
import static org.sagebionetworks.bridge.models.ResourceList.PREDICATE;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.STATUS;
import static org.sagebionetworks.bridge.models.ResourceList.STRING_SEARCH_POSITION;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.sagebionetworks.bridge.hibernate.QueryBuilder.WhereClauseBuilder;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.SearchTermPredicate;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.apps.App;

/** Hibernate implementation of Account Dao. */
@Component
public class HibernateAccountDao implements AccountDao {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);

    static final String ID_QUERY = "SELECT acct.id FROM HibernateAccount AS acct";
    static final String FULL_QUERY = "SELECT acct FROM HibernateAccount AS acct";
    static final String COUNT_QUERY = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct";
    static final String DELETE_ALL_ACCOUNTS_QUERY = "DELETE FROM Accounts WHERE studyId = :appId";
    static final String APP_IDS_FOR_USER_QUERY = "SELECT DISTINCT acct.appId FROM HibernateAccount AS acct WHERE synapseUserId = :synapseUserId";
    
    static final String EXTID_BASE_QUERY = "from HibernateEnrollment as en "
            + "WHERE en.appId = :appId AND en.studyId = :studyId "
            + "AND en.externalId IS NOT NULL";
    static final String EXTID_FILTER_QUERY = "AND en.externalId LIKE :idFilter";
    static final String EXTID_ORDER_QUERY = "ORDER BY en.externalId";
    
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
        query.append(APP_IDS_FOR_USER_QUERY, "synapseUserId", synapseUserId);
        return hibernateHelper.queryGet(query.getQuery(), query.getParameters(), null, null, String.class);
    }
    
    /** {@inheritDoc} */
    @Override
    public void createAccount(App app, Account account) {
        hibernateHelper.create(account);
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account) {
        hibernateHelper.update(account);
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
            Account updated = hibernateHelper.update(account);
            account.setVersion(updated.getVersion());
        }
        return Optional.of(account);
    }
    
    QueryBuilder makeQuery(String prefix, String appId, AccountId accountId, AccountSummarySearch search, boolean isCount) {
        RequestContext context = RequestContext.get();
        Set<String> callerStudies = context.getOrgSponsoredStudies();
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(prefix);
        builder.append("LEFT JOIN acct.enrollments AS enrollment");
        builder.append("WITH acct.id = enrollment.accountId");
        if (search != null && search.isInUse() != null) {
            builder.append("LEFT JOIN org.sagebionetworks.bridge.models.RequestInfo AS ri");
            builder.append("WITH acct.id = ri.userId");
        }
        SearchTermPredicate predicate = (search != null) ? search.getPredicate() : AND;
        WhereClauseBuilder where = builder.startWhere(predicate);
        where.appendRequired("acct.appId = :appId", "appId", appId);
        
        if (accountId != null) {
            AccountId unguarded = accountId.getUnguardedAccountId();
            if (unguarded.getEmail() != null) {
                where.appendRequired("acct.email=:email", "email", unguarded.getEmail());
            } else if (unguarded.getHealthCode() != null) {
                where.appendRequired("acct.healthCode=:healthCode","healthCode", unguarded.getHealthCode());
            } else if (unguarded.getPhone() != null) {
                where.appendRequired("acct.phone.number=:number", "number", unguarded.getPhone().getNumber());
                where.appendRequired("acct.phone.regionCode=:regionCode", "regionCode", unguarded.getPhone().getRegionCode());
            } else if (unguarded.getSynapseUserId() != null) {
                where.appendRequired("acct.synapseUserId=:synapseUserId", "synapseUserId", unguarded.getSynapseUserId());
            } else {
                where.appendRequired("enrollment.externalId=:externalId", "externalId", unguarded.getExternalId());
            }
        }
        if (search != null) {
            where.like(search.getStringSearchPosition(), "acct.email LIKE :email", "email", search.getEmailFilter());
            where.phone(search.getStringSearchPosition(), search.getPhoneFilter());
            where.append("acct.createdOn >= :startTime", "startTime", search.getStartTime());
            where.append("acct.createdOn <= :endTime", "endTime", search.getEndTime());
            where.append(":language IN ELEMENTS(acct.languages)", "language", search.getLanguage());
            where.like(search.getStringSearchPosition(), "enrollment.externalId LIKE :extId", "extId", search.getExternalIdFilter());
            where.append("acct.status = :status", "status", search.getStatus());
            where.adminOnlyRequired(search.isAdminOnly());
            where.dataGroups(search.getAllOfGroups(), "IN");
            where.dataGroups(search.getNoneOfGroups(), "NOT IN");
            where.like(search.getStringSearchPosition(), "acct.attributes['"+search.getAttributeKey()+"'] LIKE :attValue", "attValue", search.getAttributeValueFilter());
            
            // Perhaps confusing with the below enrollment code, this is a filter based on enrolled/withdrawn state.
            where.enrollment(search.getEnrollment(), true);
            
            // Search for an organization member, or search based on enrollment, either a specified study, 
            // or the studies accessible to the caller. For some app-scoped roles, the study filter is ignored.
            if (search.getOrgMembership() != null) {
                where.orgMembershipRequired(search.getOrgMembership());
            } else {
                String enrolledInStudy = search.getEnrolledInStudyId();
                if (enrolledInStudy != null) {
                    where.appendRequired("enrollment.studyId = :studyId", "studyId", enrolledInStudy);
                } else if (!callerStudies.isEmpty() && !context.isInRole(ADMIN, DEVELOPER, RESEARCHER, WORKER)) {
                    where.appendRequired("enrollment.studyId IN (:studies)", "studies", callerStudies);
                }
            }
            if (search.isInUse() != null) {
                if (Boolean.TRUE.equals(search.isInUse())) {
                    where.append("ri.signedInOn IS NOT NULL");
                } else {
                    where.append("ri.signedInOn IS NULL");
                }
            }
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
                .withRequestParam(ADMIN_ONLY, search.isAdminOnly())
                .withRequestParam(ALL_OF_GROUPS, search.getAllOfGroups())
                .withRequestParam(EMAIL_FILTER, search.getEmailFilter())
                .withRequestParam(END_TIME, search.getEndTime())
                .withRequestParam(LANGUAGE, search.getLanguage())
                .withRequestParam(NONE_OF_GROUPS, search.getNoneOfGroups())
                .withRequestParam(OFFSET_BY, search.getOffsetBy())
                .withRequestParam(ORG_MEMBERSHIP, search.getOrgMembership())
                .withRequestParam(PAGE_SIZE, search.getPageSize())
                .withRequestParam(PHONE_FILTER, search.getPhoneFilter())
                .withRequestParam(PREDICATE, search.getPredicate())
                .withRequestParam(START_TIME, search.getStartTime())
                .withRequestParam(STRING_SEARCH_POSITION, search.getStringSearchPosition())
                .withRequestParam(EXTERNAL_ID_FILTER, search.getExternalIdFilter())
                .withRequestParam(STATUS, search.getStatus())
                .withRequestParam(ENROLLMENT, search.getEnrollment())
                .withRequestParam(ATTRIBUTE_KEY, search.getAttributeKey())
                .withRequestParam(ATTRIBUTE_VALUE_FILTER, search.getAttributeValueFilter())
                .withRequestParam(ENROLLED_IN_STUDY_ID, search.getEnrolledInStudyId())
                .withRequestParam(IN_USE, search.isInUse());
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
        builder.withNote(acct.getNote());
        builder.withClientTimeZone(acct.getClientTimeZone());
        builder.withRoles(acct.getRoles());
        builder.withDataGroups(acct.getDataGroups());
        
        StudyAssociations assoc = BridgeUtils.studyAssociationsVisibleToCaller(null);
        if (acct.getId() != null) {
            assoc = BridgeUtils.studyAssociationsVisibleToCaller(acct);
        }
        builder.withExternalIds(assoc.getExternalIdsVisibleToCaller());
        builder.withStudyIds(assoc.getStudyIdsVisibleToCaller());
        return builder.build();
    }
    
    @Override
    public PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(offsetBy);
        checkNotNull(pageSize);
        
        QueryBuilder query = new QueryBuilder();
        query.append(EXTID_BASE_QUERY, "appId", appId, "studyId", studyId);
        if (StringUtils.isNotBlank(idFilter)) {
            query.append(EXTID_FILTER_QUERY, "idFilter", idFilter + "%");
        }
        query.append(EXTID_ORDER_QUERY);

        List<HibernateEnrollment> enrollments = hibernateHelper.queryGet("SELECT en " + query.getQuery(), 
                query.getParameters(), offsetBy, pageSize, HibernateEnrollment.class);

        List<ExternalIdentifierInfo> infos = enrollments.stream()
                .map(en -> new ExternalIdentifierInfo(en.getExternalId(), en.getStudyId(), true))
                .collect(Collectors.toList());

        int count = hibernateHelper.queryCount("SELECT count(en) " + query.getQuery(), query.getParameters());

        return new PagedResourceList<>(infos, count, true);
    }
    
    @Override
    public void deleteAllAccounts(String appId) {
        checkNotNull(appId);
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(DELETE_ALL_ACCOUNTS_QUERY, "appId", appId);
        
        hibernateHelper.nativeQueryUpdate(builder.getQuery(), builder.getParameters());
    }
}
