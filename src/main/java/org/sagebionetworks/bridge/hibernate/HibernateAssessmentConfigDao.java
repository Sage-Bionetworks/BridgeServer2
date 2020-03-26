package org.sagebionetworks.bridge.hibernate;

import java.util.Optional;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.AssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessmentConfig;

public class HibernateAssessmentConfigDao implements AssessmentConfigDao {
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public Optional<AssessmentConfig> getAssessmentConfig(String guid) {
        HibernateAssessmentConfig config = hibernateHelper.getById(HibernateAssessmentConfig.class, guid);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.of(AssessmentConfig.create(config));
    }

    @Override
    public AssessmentConfig updateAssessmentConfig(String appId, Assessment assessment, String guid, AssessmentConfig config) {
        HibernateAssessment hibAssessment = HibernateAssessment.create(assessment, appId);
        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(guid, config);
        HibernateAssessmentConfig retValue = hibernateHelper.executeWithExceptionHandling(hibConfig, (session) -> {
            session.persist(hibAssessment);
            session.persist(hibConfig);
            return hibConfig;
        });
        return AssessmentConfig.create(retValue);
    }

    @Override
    public AssessmentConfig customizeAssessmentConfig(String guid, AssessmentConfig config) {
        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(guid, config);
        HibernateAssessmentConfig retValue = hibernateHelper.executeWithExceptionHandling(hibConfig, (session) -> {
            session.persist(hibConfig);
            return hibConfig;
        });
        return AssessmentConfig.create(retValue);
    }
    
    @Override
    public void deleteAssessmentConfig(String guid, AssessmentConfig config) {
        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(guid, config);
        hibernateHelper.executeWithExceptionHandling(hibConfig, (session) -> {
            session.remove(hibConfig);
            return null;
        });
    }

}
