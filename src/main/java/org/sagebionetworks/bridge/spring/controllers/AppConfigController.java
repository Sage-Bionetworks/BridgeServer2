package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AppConfigService;

@CrossOrigin
@RestController("appConfigController")
public class AppConfigController extends BaseController {

    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private AppConfigService appConfigService;
    
    private ViewCache viewCache;

    @Autowired
    final void setAppConfigService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @Resource(name = "genericViewCache")
    final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }

    @GetMapping(path="/v3/studies/{studyId}/appconfig", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getStudyAppConfig(@PathVariable String studyId) {
        Study study = studyService.getStudy(studyId);
        
        RequestContext reqContext = BridgeUtils.getRequestContext();
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(reqContext.getCallerLanguages())
                .withClientInfo(reqContext.getCallerClientInfo())
                .withStudyIdentifier(study.getIdentifier())
                .build();
        
        CacheKey cacheKey = getCriteriaContextCacheKey(context);
        String json = viewCache.getView(cacheKey, () -> {
            AppConfig appConfig = appConfigService.getAppConfigForUser(context, true);
            // So we can delete all the relevant cached versions, keep track of them under the study
            cacheProvider.addCacheKeyToSet(CacheKey.appConfigList(study.getIdentifier()), cacheKey.toString());
            return appConfig;
        });
        return json;
    }
    
    @GetMapping("/v3/appconfigs")
    public ResourceList<AppConfig> getAppConfigs(@RequestParam String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        boolean includeDeletedFlag = Boolean.valueOf(includeDeleted);

        List<AppConfig> results = appConfigService.getAppConfigs(session.getStudyIdentifier(), includeDeletedFlag);

        return new ResourceList<>(results).withRequestParam(INCLUDE_DELETED_PARAM, includeDeletedFlag);
    }

    @PostMapping("/v3/appconfigs")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidVersionHolder createAppConfig() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = parseJson(AppConfig.class);
        
        AppConfig created = appConfigService.createAppConfig(session.getStudyIdentifier(), appConfig);
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        
        return new GuidVersionHolder(created.getGuid(), created.getVersion());
    }

    @GetMapping("/v3/appconfigs/{guid}")
    public AppConfig getAppConfig(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        return appConfigService.getAppConfig(session.getStudyIdentifier(), guid);
    }

    @PostMapping("/v3/appconfigs/{guid}")
    public GuidVersionHolder updateAppConfig(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        AppConfig appConfig = parseJson(AppConfig.class);
        appConfig.setGuid(guid);
        
        AppConfig updated = appConfigService.updateAppConfig(session.getStudyIdentifier(), appConfig);
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));

        return new GuidVersionHolder(updated.getGuid(), updated.getVersion());
    }
    
    @DeleteMapping("/v3/appconfigs/{guid}")
    public StatusMessage deleteAppConfig(@PathVariable String guid, @RequestParam String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            appConfigService.deleteAppConfigPermanently(session.getStudyIdentifier(), guid);
        } else {
            appConfigService.deleteAppConfig(session.getStudyIdentifier(), guid);
        }
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return new StatusMessage("App config deleted.");
    }

    private CacheKey getCriteriaContextCacheKey(CriteriaContext context) {
        ClientInfo info = context.getClientInfo();
        String appVersion = info.getAppVersion() == null ? "0" : Integer.toString(info.getAppVersion());
        String osName = info.getOsName() == null ? "" : info.getOsName();
        String studyId = context.getStudyIdentifier();
        // Languages. We don't provide a UI to create filtering criteria for these, but if they are 
        // set through our API, and they are included in the Accept-Language header, we will filter on 
        // them, so it's important they be part of the key
        String langs = BridgeUtils.SPACE_JOINER.join(context.getLanguages());
        
        return CacheKey.viewKey(AppConfig.class, appVersion, osName, langs, studyId);
    }
}
