package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Optional;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

@Component
public class AssessmentConfigService {

    private AssessmentService assessmentService;
    
    private HibernateAssessmentConfigDao dao;
    
    @Autowired
    public final void setAssessmentService(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }
    
    @Autowired
    public final void setHibernateAssessmentConfigDao(HibernateAssessmentConfigDao dao) {
        this.dao = dao;
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    public AssessmentConfig getAssessmentConfig(String appId, String guid) {
        checkArgument(isNotBlank(guid));
        
        Assessment assessment = assessmentService.getAssessmentByGuid(appId, guid);
        // checkOwnership once merged
        
        return dao.getAssessmentConfig(guid).orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
    }
    
    public AssessmentConfig updateAssessmentConfig(String guid, AssessmentConfig config) {
        checkArgument(isNotBlank(guid));
        checkNotNull(config);
        
        AssessmentConfig existing = getAssessmentConfig(guid);
        existing.setModifiedOn(getModifiedOn());
        existing.setConfig(config.getConfig());
        existing.setVersion(config.getVersion());
        
        return dao.updateAssessmentConfig(guid, config);
    }
    
    public void deleteAssessmentConfig(String guid) {
        checkArgument(isNotBlank(guid));
        
        Optional<AssessmentConfig> opt = dao.getAssessmentConfig(guid);
        if (opt.isPresent()) {
            dao.deleteAssessmentConfig(guid, opt.get());    
        }
    }
}
