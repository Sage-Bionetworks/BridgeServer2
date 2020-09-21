package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;

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
            EnrollmentFilter filter, Integer offsetBy, Integer pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM HibernateEnrollment WHERE");
        builder.append("appId = :appId AND studyId = :studyId", "appId", appId, "studyId", studyId);
        builder.enrollment(filter);
        
        int total = hibernateHelper.queryCount("SELECT COUNT(*) " + builder.getQuery(), builder.getParameters());
        
        List<HibernateEnrollment> enrollments = hibernateHelper.queryGet(builder.getQuery(),
                builder.getParameters(), offsetBy, pageSize, HibernateEnrollment.class);
        
        return new PagedResourceList<>(ImmutableList.copyOf(enrollments), total, true);
    }
}
