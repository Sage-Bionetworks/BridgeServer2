package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkOwnership;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfigCustomizer;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfigValidator;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
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
        
        return dao.getAssessmentConfig(guid).orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
    }
    
    public AssessmentConfig updateAssessmentConfig(String appId, String guid, AssessmentConfig config) {
        checkArgument(isNotBlank(guid));
        checkNotNull(config);
        
        Assessment assessment = assessmentService.getAssessmentByGuid(appId, guid);
        checkOwnership(appId, assessment.getOwnerId());
        
        AssessmentConfig existing = dao.getAssessmentConfig(guid)
                .orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
        config.setCreatedOn(existing.getCreatedOn());
        config.setModifiedOn(getModifiedOn());
        
        Validate.entityThrowingException(AssessmentConfigValidator.INSTANCE, config);
        
        // This is no longer a copy of a shared assessment because the config has been edited.
        // It also needs to be updated to reflect this.
        assessment.setOriginGuid(null);
        
        return dao.updateAssessmentConfig(appId, assessment, guid, config);
    }
    
    public AssessmentConfig customizeAssessmentConfig(String appId, String guid,
            Map<String, Map<String, JsonNode>> updates) {        
        
        if (updates == null) {
            throw new BadRequestException("Updates to configuration are missing");
        }
        Assessment assessment = assessmentService.getAssessmentByGuid(appId, guid);
        checkOwnership(appId, assessment.getOwnerId());
        
        Map<String, Set<PropertyInfo>> fields = assessment.getCustomizationFields();
        
        AssessmentConfigCustomizer customizer = new AssessmentConfigCustomizer(fields, updates);
        
        AssessmentConfig existing = dao.getAssessmentConfig(guid)
            .orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
        BridgeUtils.walk(existing.getConfig(), customizer);
        
        if (!customizer.hasUpdated()) {
            return existing;
        }
        
        Validate.entityThrowingException(AssessmentConfigValidator.INSTANCE, existing);
        
        existing.setModifiedOn(getModifiedOn());
        
        return dao.customizeAssessmentConfig(guid, existing);
    }
}
