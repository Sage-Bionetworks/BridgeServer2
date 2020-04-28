package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparingLong;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfig;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.AppConfigValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

@Component
public class AppConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(AppConfigService.class);
    
    private AppConfigDao appConfigDao;
    
    private AppConfigElementService appConfigElementService;
    
    private AppService appService;
    
    private SubstudyService substudyService;
    
    private SurveyService surveyService;
    
    private UploadSchemaService schemaService;
    
    private FileService fileService;
    
    @Autowired
    final void setAppConfigDao(AppConfigDao appConfigDao) {
        this.appConfigDao = appConfigDao;
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }
    
    @Autowired
    final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }   
    
    @Autowired
    final void setUploadSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
    }
    
    @Autowired
    final void setAppConfigElementService(AppConfigElementService appConfigElementService) {
        this.appConfigElementService = appConfigElementService;
    }
    
    @Autowired
    final void setFileService(FileService fileService) {
        this.fileService = fileService;
    }
    
    // In order to mock this value;
    protected long getCurrentTimestamp() {
        return DateUtils.getCurrentMillisFromEpoch(); 
    }
    
    // In order to mock this value;
    protected String getGUID() {
        return BridgeUtils.generateGuid();
    }
    
    public List<AppConfig> getAppConfigs(String appId, boolean includeDeleted) {
        checkNotNull(appId);
        
        return appConfigDao.getAppConfigs(appId, includeDeleted);
    }
    
    public AppConfig getAppConfig(String appId, String guid) {
        checkNotNull(appId);
        checkArgument(isNotBlank(guid));
        
        return appConfigDao.getAppConfig(appId, guid);
    }
    
    public AppConfig getAppConfigForUser(CriteriaContext context, boolean throwException) {
        checkNotNull(context);

        List<AppConfig> appConfigs = getAppConfigs(context.getAppId(), false);

        List<AppConfig> matches = CriteriaUtils.filterByCriteria(context, appConfigs,
                comparingLong(AppConfig::getCreatedOn));

        // Should have matched one and only one app config.
        if (matches.isEmpty()) {
            if (throwException) {
                throw new EntityNotFoundException(AppConfig.class);    
            } else {
                return null;
            }
        } else if (matches.size() != 1) {
            // If there is more than one match, return the one created first, but log a message
            LOG.info("CriteriaContext matches more than one app config: criteriaContext=" + context + ", appConfigs="+matches);
        }
        AppConfig matched = matches.get(0);
        // Resolve survey references to pick up survey identifiers
        matched.setSurveyReferences(matched.getSurveyReferences().stream()
            .map(surveyReference -> resolveSurvey(context.getAppId(), surveyReference))
            .collect(Collectors.toList()));
        
        ImmutableMap.Builder<String, JsonNode> builder = new ImmutableMap.Builder<>();
        for (ConfigReference configRef : matched.getConfigReferences()) {
            AppConfigElement element = retrieveConfigElement(context.getAppId(), configRef, matched.getGuid());
            if (element != null) {
                builder.put(configRef.getId(), element.getData());    
            }
        }
        matched.setConfigElements(builder.build());

        return matched;
    }

    protected AppConfigElement retrieveConfigElement(String appId, ConfigReference configRef, String appConfigGuid) {
        try {
            return appConfigElementService.getElementRevision(appId, configRef.getId(), configRef.getRevision());
        } catch(EntityNotFoundException e) {
            String message = String.format("AppConfig[guid=%s] references missing AppConfigElement[id=%s, revision=%d]",
                    appConfigGuid, configRef.getId(), configRef.getRevision());
            logError(message);
        }
        return null;
    }
    
    protected void logError(String message) {
        LOG.error(message);
    }

    /**
     * Survey and schema references in an AppConfig are "hard" references... they must reference a
     * specific version or createdOn timestamp of a version, and we validate this when creating/
     * updating the app config. We're only concerned with adding the survey identifier here.
     */
    SurveyReference resolveSurvey(String appId, SurveyReference surveyRef) {
        if (surveyRef.getIdentifier() != null) {
            return surveyRef;
        }
        GuidCreatedOnVersionHolder surveyKeys = new GuidCreatedOnVersionHolderImpl(surveyRef);
        Survey survey = surveyService.getSurvey(appId, surveyKeys, false, false);
        if (survey != null) {
            return new SurveyReference(survey.getIdentifier(), survey.getGuid(), new DateTime(survey.getCreatedOn()));    
        }
        return surveyRef;
    }
    
    public AppConfig createAppConfig(String appId, AppConfig appConfig) {
        checkNotNull(appId);
        checkNotNull(appConfig);
        
        appConfig.setAppId(appId);
        
        App app = appService.getApp(appId);
        
        Set<String> substudyIds = substudyService.getSubstudyIds(app.getIdentifier());
        
        Validator validator = new AppConfigValidator(surveyService, schemaService, appConfigElementService, 
                fileService, app.getDataGroups(), substudyIds, true);
        Validate.entityThrowingException(validator, appConfig);

        long timestamp = getCurrentTimestamp();

        DynamoAppConfig newAppConfig = new DynamoAppConfig();
        newAppConfig.setLabel(appConfig.getLabel());
        newAppConfig.setAppId(appConfig.getAppId());
        newAppConfig.setCriteria(appConfig.getCriteria());
        newAppConfig.setClientData(appConfig.getClientData());
        newAppConfig.setSurveyReferences(appConfig.getSurveyReferences());
        newAppConfig.setSchemaReferences(appConfig.getSchemaReferences());
        newAppConfig.setConfigReferences(appConfig.getConfigReferences());
        newAppConfig.setFileReferences(appConfig.getFileReferences());
        newAppConfig.setCreatedOn(timestamp);
        newAppConfig.setModifiedOn(timestamp);
        newAppConfig.setGuid(getGUID());
        
        appConfigDao.createAppConfig(newAppConfig);
        newAppConfig.setVersion(newAppConfig.getVersion());
        return newAppConfig;
    }
    
    public AppConfig updateAppConfig(String appId, AppConfig appConfig) {
        checkNotNull(appId);
        checkNotNull(appConfig);
        
        appConfig.setAppId(appId);
        
        App app = appService.getApp(appId);
        
        Set<String> substudyIds = substudyService.getSubstudyIds(app.getIdentifier());
        
        Validator validator = new AppConfigValidator(surveyService, schemaService, appConfigElementService, 
                fileService, app.getDataGroups(), substudyIds, false);
        Validate.entityThrowingException(validator, appConfig);
        
        // Throw a 404 if the GUID is not valid.
        AppConfig persistedConfig = appConfigDao.getAppConfig(appId, appConfig.getGuid());
        appConfig.setCreatedOn(persistedConfig.getCreatedOn());
        appConfig.setModifiedOn(getCurrentTimestamp());
        
        return appConfigDao.updateAppConfig(appConfig);
    }
    
    public void deleteAppConfig(String appId, String guid) {
        checkNotNull(appId);
        checkArgument(isNotBlank(guid));
        
        appConfigDao.deleteAppConfig(appId, guid);
    }
    
    public void deleteAppConfigPermanently(String appId, String guid) {
        checkNotNull(appId);
        checkArgument(isNotBlank(guid));
        
        appConfigDao.deleteAppConfigPermanently(appId, guid);
    }
}
