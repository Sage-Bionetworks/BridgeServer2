package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.BridgeUtils.checkOwnership;
import static org.sagebionetworks.bridge.BridgeUtils.checkSharedOwnership;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfigCustomizer;
import org.sagebionetworks.bridge.models.assessments.PropertyInfo;
import org.sagebionetworks.bridge.validators.AssessmentConfigValidator;
import org.sagebionetworks.bridge.validators.Validate;

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
        checkOwnership(appId, assessment.getOwnerId());
        
        return dao.getAssessmentConfig(guid).orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
    }
    
    public AssessmentConfig getSharedAssessmentConfig(String callerAppId, String guid) {
        checkArgument(isNotBlank(guid));
        
        Assessment assessment = assessmentService.getAssessmentByGuid(SHARED_STUDY_ID_STRING, guid);
        checkSharedOwnership(callerAppId, guid, assessment.getOwnerId());
        
        return dao.getAssessmentConfig(guid).orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
    }
    
    public AssessmentConfig updateAssessmentConfig(String appId, String guid, AssessmentConfig config) {
        checkArgument(isNotBlank(guid));
        checkNotNull(config);
        
        Assessment assessment = assessmentService.getAssessmentByGuid(SHARED_STUDY_ID_STRING, guid);
        checkSharedOwnership(appId, guid, assessment.getOwnerId());
        
        AssessmentConfig existing = getAssessmentConfig(appId, guid);
        existing.setModifiedOn(getModifiedOn());
        existing.setConfig(config.getConfig());
        existing.setVersion(config.getVersion());
        
        Validate.entityThrowingException(AssessmentConfigValidator.INSTANCE, existing);
        
        // This is no longer a copy of a shared assessment because the config has been edited.
        assessment.setOriginGuid(null);
        
        return dao.updateAssessmentConfig(guid, assessment, existing);
    }
    
    public AssessmentConfig customizeAssessmentConfig(String appId, String guid,
            Map<String, Map<String, JsonNode>> updates) {        
        
        Assessment assessment = assessmentService.getAssessmentByGuid(appId, guid);
        checkOwnership(appId, assessment.getOwnerId());
        
        Map<String, Set<PropertyInfo>> fields = assessment.getCustomizationFields();
        
        AssessmentConfigCustomizer customizer = new AssessmentConfigCustomizer(fields, updates);
        
        AssessmentConfig existing = getAssessmentConfig(appId, guid);
        BridgeUtils.walk(existing.getConfig(), customizer);
        
        if (!customizer.hasUpdated()) {
            return existing;
        }
        
        Validate.entityThrowingException(AssessmentConfigValidator.INSTANCE, existing);
        
        existing.setModifiedOn(getModifiedOn());
        
        return dao.customizeAssessmentConfig(guid, existing);
    }
}
