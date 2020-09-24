package org.sagebionetworks.bridge.hibernate;

import static java.util.stream.Collectors.toList;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.EnrollmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

@Component
public class HibernateEnrollmentDao implements EnrollmentDao {

    private HibernateHelper hibernateHelper;
    
    private AccountDao accountDao;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Override
    public PagedResourceList<EnrollmentDetail> getEnrollmentsForStudy(String appId, String studyId, 
            EnrollmentFilter filter, Integer offsetBy, Integer pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM HibernateEnrollment WHERE");
        builder.append("appId = :appId AND studyId = :studyId", "appId", appId, "studyId", studyId);
        builder.enrollment(filter);
        
        int total = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        
        List<HibernateEnrollment> enrollments = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, HibernateEnrollment.class);
        
        List<EnrollmentDetail> dtos = enrollments.stream().map(enrollment -> {
            AccountRef participantRef = nullSafeAccountRef(appId, enrollment.getAccountId());
            AccountRef enrolledByRef = nullSafeAccountRef(appId, enrollment.getEnrolledBy());
            AccountRef withdrawnByRef = nullSafeAccountRef(appId, enrollment.getWithdrawnBy());
            return new EnrollmentDetail(enrollment, participantRef, enrolledByRef, withdrawnByRef);
        }).collect(toList());
        return new PagedResourceList<>(dtos, total, true);
    }
    
    private AccountRef nullSafeAccountRef(String appId, String id) {
        if (id == null) {
            return null;
        }
        AccountId accountId = AccountId.forId(appId, id);
        Account account = accountDao.getAccount(accountId).orElse(null);
        if (account == null) {
            return null;
        }
        return new AccountRef(account);
    }
}
