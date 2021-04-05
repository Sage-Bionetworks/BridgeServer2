package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;

import java.util.List;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.EnrollmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

@Component
public class HibernateEnrollmentDao implements EnrollmentDao {

    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Override
    public PagedResourceList<Enrollment> getEnrollmentsForStudy(String appId, String studyId, 
            EnrollmentFilter filter, boolean includeTesters, Integer offsetBy, Integer pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM HibernateEnrollment AS h");
        if (!includeTesters) {
            builder.append("INNER JOIN org.sagebionetworks.bridge.hibernate.HibernateAccount AS acct ON acct.id = h.accountId");    
        }
        builder.append("WHERE h.appId = :appId AND h.studyId = :studyId", "appId", appId, "studyId", studyId);
        builder.enrollment(filter);
        if (!includeTesters) {
            builder.dataGroups(ImmutableSet.of(TEST_USER_GROUP), "NOT IN");
        }
        int total = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        
        List<HibernateEnrollment> enrollments = hibernateHelper.queryGet("SELECT h " + builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, HibernateEnrollment.class);
        
        return new PagedResourceList<>(ImmutableList.copyOf(enrollments), total);
    }
    
    @Override
    public List<Enrollment> getEnrollmentsForUser(String appId, String userId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM HibernateEnrollment WHERE");
        builder.append("appId = :appId AND accountId = :userId", "appId", appId, "userId", userId);
        
        List<HibernateEnrollment> enrollments = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), null, null, HibernateEnrollment.class);
        return ImmutableList.copyOf(enrollments);
    }
}
