package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import javax.annotation.Resource;

import com.google.common.base.Supplier;

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

import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.time.DateUtils;

@CrossOrigin
@RestController
public class SurveyController extends BaseController {

    static final StatusMessage DELETED_MSG = new StatusMessage("Survey deleted.");
    static final String INCLUDE_DELETED = "includeDeleted";
    public static final String MOSTRECENT_KEY = "mostrecent";
    public static final String PUBLISHED_KEY = "published";

    private SurveyService surveyService;

    private ViewCache viewCache;

    @Autowired
    final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @Resource(name = "genericViewCache")
    final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }

    @GetMapping("/v3/surveys")
    public ResourceList<Survey> getAllSurveysMostRecentVersion(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        String appId = session.getAppId();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentVersion(appId, includeDeleted);
        return new ResourceList<>(surveys);
    }

    @GetMapping("/v3/surveys/published")
    public ResourceList<Survey> getAllSurveysMostRecentlyPublishedVersion(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        List<Survey> surveys = surveyService.getAllSurveysMostRecentlyPublishedVersion(appId, includeDeleted);
        return new ResourceList<>(surveys);
    }

    /**
     * API for worker accounts that need access to a list of published apps. This is generally used by the Bridge
     * Exporter. We don't want to configure worker accounts for each app and add an ever-growing list of worker
     * accounts to back-end scripts, so we'll have one master worker account in the API app that can access all
     * apps.
     *
     * @param appId
     *            app to get surveys for
     * @return list of the most recently published version of every survey in the app
     */
    @GetMapping(path = {"/v1/apps/{appId}/surveys/published", "/v3/studies/{appId}/surveys/published"})
    public ResourceList<Survey> getAllSurveysMostRecentlyPublishedVersionForApp(@PathVariable String appId,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        getAuthenticatedSession(WORKER);

        List<Survey> surveyList = surveyService
                .getAllSurveysMostRecentlyPublishedVersion(appId, includeDeleted);
        return new ResourceList<>(surveyList);
    }

    @GetMapping(path="/api/v2/surveys/{surveyGuid}/revisions/published", produces={APPLICATION_JSON_VALUE})
    public String getSurveyMostRecentlyPublishedVersionForUser(@PathVariable String surveyGuid) {
        UserSession session = getAuthenticatedAndConsentedSession();

        return getCachedSurveyMostRecentlyPublishedInternal(surveyGuid, session);
    }

    @GetMapping(path="/v3/surveys/{surveyGuid}/revisions/{createdOn}", produces={APPLICATION_JSON_VALUE})
    public String getSurvey(@PathVariable String surveyGuid, @PathVariable String createdOn) throws Exception {
        UserSession session = getSessionEitherConsentedOrInRole(WORKER, DEVELOPER);
        if (session.isInRole(WORKER)) {
            // Worker accounts can access surveys across apps. We branch off and call getSurveyForWorker().
            return MAPPER.writeValueAsString(getSurveyForWorker(surveyGuid, createdOn));
        } else {
            return getCachedSurveyInternal(surveyGuid, createdOn, session);
        }
    }

    /**
     * <p>
     * Worker API to get a survey by guid and createdOn timestamp. This is used by the Bridge Exporter to get survey
     * questions for a particular survey. This is separate from getSurvey() and getSurveyForUser() as it needs to get
     * surveys for any app.
     * </p>
     * <p>
     * Bridge Exporter only calls this API for surveys it hasn't seen before, so we should be okay to not cache this.
     * </p>
     *
     * @param surveyGuid
     *            GUID of survey to fetch
     * @param createdOnString
     *            the created on (versioned on) timestamp
     * @return survey result
     */
    private Survey getSurveyForWorker(String surveyGuid, String createdOnString) {
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);
        return surveyService.getSurvey(null, keys, true, true);
    }

    @GetMapping(path="/api/v2/surveys/{surveyGuid}/revisions/{createdOn}", produces={APPLICATION_JSON_VALUE})
    public String getSurveyForUser(@PathVariable String surveyGuid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedAndConsentedSession();

        return getCachedSurveyInternal(surveyGuid, createdOn, session);
    }

    @GetMapping(path="/v3/surveys/{surveyGuid}/revisions/recent", produces={APPLICATION_JSON_VALUE})
    public String getSurveyMostRecentVersion(@PathVariable String surveyGuid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        CacheKey cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, MOSTRECENT_KEY, appId);

        return getView(cacheKey, session, () -> {
            return surveyService.getSurveyMostRecentVersion(appId, surveyGuid);
        });
    }

    @GetMapping(path="/v3/surveys/{surveyGuid}/revisions/published", produces={APPLICATION_JSON_VALUE})
    public String getSurveyMostRecentlyPublishedVersion(@PathVariable String surveyGuid) {
        UserSession session = getSessionEitherConsentedOrInRole(DEVELOPER);

        return getCachedSurveyMostRecentlyPublishedInternal(surveyGuid, session);
    }

    /**
     * Administrators can pass the ?physical=true flag to this endpoint to physically delete a survey and all its survey
     * elements, rather than only marking it deleted to maintain referential integrity. This should only be used as part
     * of testing.
     * 
     * @param surveyGuid
     * @param createdOnString
     * @param physical
     * @return
     */
    @DeleteMapping("/v3/surveys/{surveyGuid}/revisions/{createdOn}")
    public StatusMessage deleteSurvey(@PathVariable String surveyGuid, @PathVariable String createdOn,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        long createdOnLong = DateUtils.convertToMillisFromEpoch(createdOn);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOnLong);

        // This call will return logically deleted surveys, which allows them to be physically deleted.
        Survey survey = surveyService.getSurvey(appId, keys, false, false);
        if (survey == null) {
            throw new EntityNotFoundException(Survey.class);
        }
        if (physical && session.isInRole(ADMIN)) {
            surveyService.deleteSurveyPermanently(appId, survey);
        } else {
            surveyService.deleteSurvey(appId, survey);
        }
        expireCache(surveyGuid, createdOn, appId);
        return DELETED_MSG;
    }

    @GetMapping("/v3/surveys/{surveyGuid}/revisions")
    public ResourceList<Survey> getSurveyAllVersions(@PathVariable String surveyGuid,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        List<Survey> surveys = surveyService.getSurveyAllVersions(appId, surveyGuid, includeDeleted);
        return new ResourceList<>(surveys);
    }

    @PostMapping("/v3/surveys")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidCreatedOnVersionHolder createSurvey() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        Survey survey = parseJson(Survey.class);
        survey.setAppId(appId);

        survey = surveyService.createSurvey(survey);
        return new GuidCreatedOnVersionHolderImpl(survey);
    }

    @PostMapping("/v3/surveys/{surveyGuid}/revisions/{createdOn}/version")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidCreatedOnVersionHolder versionSurvey(@PathVariable String surveyGuid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        long createdOnLong = DateUtils.convertToMillisFromEpoch(createdOn);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOnLong);

        Survey survey = surveyService.versionSurvey(session.getAppId(), keys);
        expireCache(surveyGuid, createdOn, appId);

        return new GuidCreatedOnVersionHolderImpl(survey);
    }

    @PostMapping("/v3/surveys/{surveyGuid}/revisions/{createdOn}")
    public GuidCreatedOnVersionHolder updateSurvey(@PathVariable String surveyGuid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        // The parameters in the URL take precedence over anything declared in
        // the object itself.
        Survey survey = parseJson(Survey.class);
        survey.setGuid(surveyGuid);
        survey.setCreatedOn(DateUtils.convertToMillisFromEpoch(createdOn));
        survey.setAppId(appId);

        survey = surveyService.updateSurvey(session.getAppId(), survey);
        expireCache(surveyGuid, createdOn, appId);

        return new GuidCreatedOnVersionHolderImpl(survey);
    }

    @PostMapping("/v3/surveys/{surveyGuid}/revisions/{createdOn}/publish")
    public GuidCreatedOnVersionHolder publishSurvey(@PathVariable String surveyGuid, @PathVariable String createdOn,
            @RequestParam(defaultValue = "false") boolean newSchemaRev) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getAppId();

        long createdOnLong = DateUtils.convertToMillisFromEpoch(createdOn);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOnLong);

        Survey survey = surveyService.publishSurvey(appId, keys, newSchemaRev);
        expireCache(surveyGuid, createdOn, appId);

        return new GuidCreatedOnVersionHolderImpl(survey);
    }

    private String getCachedSurveyInternal(String surveyGuid, String createdOnString, UserSession session) {
        long createdOn = DateUtils.convertToMillisFromEpoch(createdOnString);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(surveyGuid, createdOn);

        CacheKey cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, createdOnString,
                session.getAppId());

        return getView(cacheKey, session, () -> {
            return surveyService.getSurvey(session.getAppId(), keys, true, true);
        });
    }

    private String getCachedSurveyMostRecentlyPublishedInternal(String surveyGuid, UserSession session) {
        CacheKey cacheKey = viewCache.getCacheKey(Survey.class, surveyGuid, PUBLISHED_KEY,
                session.getAppId());

        return getView(cacheKey, session, () -> {
            return surveyService.getSurveyMostRecentlyPublishedVersion(session.getAppId(), surveyGuid, true);
        });
    }

    private String getView(CacheKey cacheKey, UserSession session, Supplier<Survey> supplier) {
        return viewCache.getView(cacheKey, () -> {
            return supplier.get();
        });
    }

    private void expireCache(String surveyGuid, String createdOnString, String appId) {
        // Don't screw around trying to figure out if *this* survey instance is the same survey
        // as the most recent or published version, expire all versions in the cache
        viewCache.removeView(viewCache.getCacheKey(Survey.class, surveyGuid, createdOnString, appId));
        viewCache.removeView(viewCache.getCacheKey(Survey.class, surveyGuid, PUBLISHED_KEY, appId));
        viewCache.removeView(viewCache.getCacheKey(Survey.class, surveyGuid, MOSTRECENT_KEY, appId));
    }
}