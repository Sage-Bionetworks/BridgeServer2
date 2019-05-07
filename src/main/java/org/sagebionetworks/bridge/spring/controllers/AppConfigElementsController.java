package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

import java.util.List;

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
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.services.AppConfigElementService;

@CrossOrigin
@RestController("appConfigElementsController")
public class AppConfigElementsController extends BaseController {

    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private AppConfigElementService service;
    
    @Autowired
    final void setAppConfigElementService(AppConfigElementService service) {
        this.service = service;
    }
    
    @GetMapping("/v3/appconfigs/elements")
    public ResourceList<AppConfigElement> getMostRecentElements(
            @RequestParam(name = INCLUDE_DELETED_PARAM, defaultValue = "false") String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<AppConfigElement> elements = service.getMostRecentElements(session.getStudyIdentifier(),
                Boolean.valueOf(includeDeleted));
        
        return new ResourceList<AppConfigElement>(elements)
                .withRequestParam(INCLUDE_DELETED_PARAM, includeDeleted);
    }
    
    @PostMapping("/v3/appconfigs/elements")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createElement() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        AppConfigElement element = parseJson(AppConfigElement.class);
        
        VersionHolder version = service.createElement(session.getStudyIdentifier(), element);

        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return version;
    }
    
    @GetMapping("/v3/appconfigs/elements/{id}")
    public ResourceList<AppConfigElement> getElementRevisions(@PathVariable String id,
            @RequestParam(name = INCLUDE_DELETED_PARAM, defaultValue = "false") String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<AppConfigElement> elements = service.getElementRevisions(session.getStudyIdentifier(), id,
                Boolean.valueOf(includeDeleted));
        
        return new ResourceList<AppConfigElement>(elements)
                .withRequestParam(INCLUDE_DELETED_PARAM, includeDeleted);
    }
    
    @GetMapping("/v3/appconfigs/elements/{id}/recent")
    public AppConfigElement getMostRecentElement(@PathVariable String id) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        return service.getMostRecentElement(session.getStudyIdentifier(), id);
    }

    @GetMapping("/v3/appconfigs/elements/{id}/revisions/{revision}")
    public AppConfigElement getElementRevision(@PathVariable String id,
            @PathVariable String revision) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Long revisionLong = BridgeUtils.getLongOrDefault(revision, null);
        if (revisionLong == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        return service.getElementRevision(session.getStudyIdentifier(), id, revisionLong);
    }
    
    @PostMapping("/v3/appconfigs/elements/{id}/revisions/{revision}")
    public VersionHolder updateElementRevision(@PathVariable String id,
            @PathVariable String revision) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Long revisionLong = BridgeUtils.getLongOrDefault(revision, null);
        if (revisionLong == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        
        AppConfigElement element = parseJson(AppConfigElement.class);
        element.setId(id);
        element.setRevision(revisionLong);
        
        VersionHolder holder = service.updateElementRevision(session.getStudyIdentifier(), element);

        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return holder;
    }

    @DeleteMapping("/v3/appconfigs/elements/{id}")
    public StatusMessage deleteElementAllRevisions(@PathVariable String id,
            @RequestParam(name = "physical", defaultValue = "false") String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteElementAllRevisionsPermanently(session.getStudyIdentifier(), id);
        } else {
            service.deleteElementAllRevisions(session.getStudyIdentifier(), id);
        }
        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return new StatusMessage("App config element deleted.");
    }

    @DeleteMapping("/v3/appconfigs/elements/{id}/revisions/{revision}")
    public StatusMessage deleteElementRevision(@PathVariable String id, @PathVariable String revision,
            @RequestParam(name = "physical", defaultValue = "false") String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        Long revisionLong = BridgeUtils.getLongOrDefault(revision, null);
        if (revisionLong == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteElementRevisionPermanently(session.getStudyIdentifier(), id, revisionLong);
        } else {
            service.deleteElementRevision(session.getStudyIdentifier(), id, revisionLong);
        }
        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getStudyIdentifier()));
        return new StatusMessage("App config element revision deleted.");
    }
    
}
