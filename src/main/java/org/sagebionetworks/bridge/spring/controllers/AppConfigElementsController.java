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
    public ResourceList<AppConfigElement> getMostRecentElements(@RequestParam String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        boolean includeDeletedFlag = Boolean.valueOf(includeDeleted);

        List<AppConfigElement> elements = service.getMostRecentElements(session.getAppId(), includeDeletedFlag);

        return new ResourceList<AppConfigElement>(elements).withRequestParam(INCLUDE_DELETED_PARAM, includeDeletedFlag);
    }

    @PostMapping("/v3/appconfigs/elements")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionHolder createElement() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        AppConfigElement element = parseJson(AppConfigElement.class);

        VersionHolder version = service.createElement(session.getAppId(), element);

        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getAppId()));
        return version;
    }

    @GetMapping("/v3/appconfigs/elements/{id}")
    public ResourceList<AppConfigElement> getElementRevisions(@PathVariable String id,
            @RequestParam String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        boolean includeDeletedFlag = Boolean.valueOf(includeDeleted);
        List<AppConfigElement> elements = service.getElementRevisions(session.getAppId(), id, includeDeletedFlag);

        return new ResourceList<AppConfigElement>(elements).withRequestParam(INCLUDE_DELETED_PARAM, includeDeletedFlag);
    }

    @GetMapping("/v3/appconfigs/elements/{id}/recent")
    public AppConfigElement getMostRecentElement(@PathVariable String id) throws Exception {
        UserSession session = getAuthenticatedSession();

        return service.getMostRecentElement(session.getAppId(), id);
    }

    @GetMapping("/v3/appconfigs/elements/{id}/revisions/{revision}")
    public AppConfigElement getElementRevision(@PathVariable String id, @PathVariable String revision) {
        UserSession session = getAuthenticatedSession();

        Long revisionLong = BridgeUtils.getLongOrDefault(revision, null);
        if (revisionLong == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }
        return service.getElementRevision(session.getAppId(), id, revisionLong);
    }

    @PostMapping("/v3/appconfigs/elements/{id}/revisions/{revision}")
    public VersionHolder updateElementRevision(@PathVariable String id, @PathVariable String revision) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Long revisionLong = BridgeUtils.getLongOrDefault(revision, null);
        if (revisionLong == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }

        AppConfigElement element = parseJson(AppConfigElement.class);
        element.setId(id);
        element.setRevision(revisionLong);

        VersionHolder holder = service.updateElementRevision(session.getAppId(), element);

        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getAppId()));
        return holder;
    }

    @DeleteMapping("/v3/appconfigs/elements/{id}")
    public StatusMessage deleteElementAllRevisions(@PathVariable String id, @RequestParam String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);

        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteElementAllRevisionsPermanently(session.getAppId(), id);
        } else {
            service.deleteElementAllRevisions(session.getAppId(), id);
        }
        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getAppId()));
        return new StatusMessage("App config element deleted.");
    }

    @DeleteMapping("/v3/appconfigs/elements/{id}/revisions/{revision}")
    public StatusMessage deleteElementRevision(@PathVariable String id, @PathVariable String revision,
            @RequestParam String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);

        Long revisionLong = BridgeUtils.getLongOrDefault(revision, null);
        if (revisionLong == null) {
            throw new BadRequestException("Revision is not a valid revision number");
        }

        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteElementRevisionPermanently(session.getAppId(), id, revisionLong);
        } else {
            service.deleteElementRevision(session.getAppId(), id, revisionLong);
        }
        // App config elements are included in the app configs, so allow cache to update
        cacheProvider.removeSetOfCacheKeys(CacheKey.appConfigList(session.getAppId()));
        return new StatusMessage("App config element revision deleted.");
    }

}
