package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ASSESSMENTS;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HibernateAssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfigCustomizer;
import org.sagebionetworks.bridge.validators.AssessmentConfigValidator;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class AssessmentConfigService {

    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private HibernateAssessmentConfigDao dao;
    @Autowired
    private CacheProvider cacheProvider;
    
    // Allow timestamp to be mocked
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    // Allow validator to be mocked
    AssessmentConfigValidator getValidator() {
        return AssessmentConfigValidator.INSTANCE;
    }
    
    /**
     * Get an assessment’s config. We do not check ownership to read an assessment 
     * configuration. Anyone can read any configuration.
     */
    public AssessmentConfig getAssessmentConfig(String appId, String guid) {
        checkArgument(isNotBlank(guid));
        
        assessmentService.getAssessmentByGuid(appId, null, guid);
        
        AssessmentConfig config = dao.getAssessmentConfig(guid)
                .orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
        
        // It hasn't changed, but it will prime the cache faster if we write this value on reads
        CacheKey cacheKey = CacheKey.etag(AssessmentConfig.class, guid);
        cacheProvider.setObject(cacheKey, config.getModifiedOn());
        
        return config;
    }
    
    public AssessmentConfig getSharedAssessmentConfig(String callerAppId, String guid) {
        checkArgument(isNotBlank(guid));
        
        AssessmentConfig config = dao.getAssessmentConfig(guid)
                .orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
        
        // It hasn't changed, but it will prime the cache faster if we write this value on reads
        CacheKey cacheKey = CacheKey.etag(AssessmentConfig.class, guid);
        cacheProvider.setObject(cacheKey, config.getModifiedOn());
        
        return config;
    }
    
    public AssessmentConfig updateAssessmentConfig(String appId, String ownerId, String guid, AssessmentConfig config) {
        checkArgument(isNotBlank(guid));
        checkNotNull(config);
        
        Assessment assessment = assessmentService.getAssessmentByGuid(appId, ownerId, guid);
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, assessment.getOwnerId());
        
        AssessmentConfig existing = dao.getAssessmentConfig(guid)
                .orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
        config.setCreatedOn(existing.getCreatedOn());
        config.setModifiedOn(getModifiedOn());
        
        Validate.entityThrowingException(getValidator(), config);
        
        // This is no longer a copy of a shared assessment because the config has been edited.
        // It also needs to be updated to reflect this.
        assessment.setOriginGuid(null);
        
        AssessmentConfig updatedConfig = dao.updateAssessmentConfig(appId, assessment, guid, config);
        
        CacheKey cacheKey = CacheKey.etag(AssessmentConfig.class, assessment.getGuid());
        cacheProvider.setObject(cacheKey, config.getModifiedOn());
        
        return updatedConfig;
    }
    
    public AssessmentConfig customizeAssessmentConfig(String appId, String ownerId,
            String guid, Map<String, Map<String, JsonNode>> updates) {        
        
        if (updates == null) {
            throw new BadRequestException("Updates to configuration are missing");
        }
        Assessment assessment = assessmentService.getAssessmentByGuid(appId, ownerId, guid);
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, assessment.getOwnerId());
        
        Map<String, Set<PropertyInfo>> fields = assessment.getCustomizationFields();
        
        AssessmentConfigCustomizer customizer = new AssessmentConfigCustomizer(fields, updates);
        
        AssessmentConfig existing = dao.getAssessmentConfig(guid)
            .orElseThrow(() -> new EntityNotFoundException(AssessmentConfig.class));
        BridgeUtils.walk(existing.getConfig(), customizer);
        
        if (!customizer.hasUpdated()) {
            return existing;
        }
        
        Validate.entityThrowingException(getValidator(), existing);
        
        existing.setModifiedOn(getModifiedOn());
        
        AssessmentConfig updatedConfig = dao.customizeAssessmentConfig(guid, existing);
        
        CacheKey cacheKey = CacheKey.etag(AssessmentConfig.class, assessment.getGuid());
        cacheProvider.setObject(cacheKey, existing.getModifiedOn());
        
        return updatedConfig;
    }
}
