package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.util.Throwables;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.CriteriaContext.Builder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.time.DateUtils;

public abstract class BaseController {
    
    protected final static ObjectMapper MAPPER = BridgeObjectMapper.get();

    CacheProvider cacheProvider;
    
    BridgeConfig bridgeConfig;

    AccountDao accountDao;

    StudyService studyService;

    AuthenticationService authenticationService;
    
    SessionUpdateService sessionUpdateService;
    
    RequestInfoService requestInfoService;
    
    @Autowired
    final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @Autowired
    final void setSessionUpdateService(SessionUpdateService sessionUpdateService) {
        this.sessionUpdateService = sessionUpdateService;
    }
    
    @Autowired
    final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }
    
    protected HttpServletRequest request() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        if (request == null) {
            throw new IllegalStateException("Request cannot be found in ThreadLocal context");
        }
        return request;
    }

    protected HttpServletResponse response() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    }
    
    /**
     * Returns a session. Will not throw exception if user is not authorized or has not consented to research.
     * @return session if it exists, or null otherwise.
     */
    UserSession getSessionIfItExists() {
        final String sessionToken = getSessionToken();
        if (StringUtils.isBlank(sessionToken)){
            return null;
        }
        final UserSession session = authenticationService.getSession(sessionToken);
        writeSessionInfoToMetrics(session);
        return session;
    }

    /**
     * Retrieve user's session using the Bridge-Session header, throwing an exception if the session doesn't
     * exist (user not authorized), consent has not been given or the client app version is not supported.
     */
    UserSession getAuthenticatedAndConsentedSession() throws NotAuthenticatedException, ConsentRequiredException, UnsupportedVersionException {
        return getAuthenticatedSession(true);
    }

    /**
     * Retrieve a user's session or throw an exception if the user is not authenticated. User does not have to give
     * consent. If roles are provided, user must have one of the specified roles or an authorization exception will be
     * thrown. If no roles are supplied, the user just needs to be authenticated.
     */
    UserSession getAuthenticatedSession(Roles... roles) throws NotAuthenticatedException, UnauthorizedException {
        return getAuthenticatedSession(false, roles);
    }
    
    /**
     * Return a session if the user is a consented participant, OR if the user has one of the supplied roles. If no
     * roles are supplied, this method returns the session only if the caller is a consented participant.
     */
    UserSession getSessionEitherConsentedOrInRole(Roles... roles) throws NotAuthenticatedException,
            ConsentRequiredException, UnsupportedVersionException, UnauthorizedException {
        
        return getAuthenticatedSession(true, roles);
    }
    
    /**
     * This method centralizes session checking. If consent is required, user must be consented, if roles are supplied,
     * the user must have one of the roles, and if both are provided, the user must be EITHER consented OR in one of the
     * given roles. If neither is supplied (<code>getAuthenticatedSession(false)</code>), than you just need to be
     * authenticated. This method also ensures that the user's app version is up-to-date if consent is required.
     */
    UserSession getAuthenticatedSession(boolean consentRequired, Roles...roles) {
        final UserSession session = getSessionIfItExists();
        if (session == null || !session.isAuthenticated()) {
            throw new NotAuthenticatedException();
        }
        
        // This request has required the presence of a session, so we add additional information about the user to 
        // the existing request context (which starts with only the information present in HTTP headers). This will 
        // be removed from the thread local if an exception is thrown.
        RequestContext.Builder builder = BridgeUtils.getRequestContext().toBuilder();
        // If the user has already persisted languages, we'll use that instead of the Accept-Language header
        builder.withCallerLanguages(getLanguages(session));
        builder.withCallerHealthCode(session.getHealthCode());
        builder.withCallerStudyId(session.getStudyIdentifier());
        builder.withCallerSubstudies(session.getParticipant().getSubstudyIds());
        builder.withCallerRoles(session.getParticipant().getRoles());
        builder.withCallerUserId(session.getParticipant().getId());
        RequestContext context = builder.build();
        BridgeUtils.setRequestContext(context);
        
        // Sessions are locked to an IP address if (a) it is enabled in the study for unprivileged participant accounts
        // or (b) always for privileged accounts.
        Study study = studyService.getStudy(session.getStudyIdentifier());
        Set<Roles> userRoles = session.getParticipant().getRoles();
        boolean userHasRoles = !userRoles.isEmpty();
        if (study.isParticipantIpLockingEnabled() || userHasRoles) {
            String sessionIpAddress = session.getIpAddress();
            String requestIpAddress = context.getCallerIpAddress();
            if (!Objects.equals(sessionIpAddress, requestIpAddress)) {
                throw new NotAuthenticatedException();
            }
        }

        // Any method that can throw a 412 can also throw a 410 (min app version not met).
        if (consentRequired) {
            verifySupportedVersionOrThrowException(study);
        }

        // if there are roles, they are required
        boolean rolesRequired = (roles != null && roles.length > 0); 
        boolean isInRole = (rolesRequired) ? !Collections.disjoint(Sets.newHashSet(roles), userRoles) : false;
        
        if ((consentRequired && session.doesConsent()) || (rolesRequired && isInRole)) {
            return session;
        }

        // Behavior here is unusual. It privileges the UnauthorizedException first for users with roles, 
        // and the ConsentRequiredException first for users without any roles.
        if (userHasRoles && rolesRequired && !isInRole) {
            throw new UnauthorizedException();
        }
        if (consentRequired && !session.doesConsent()) {
            throw new ConsentRequiredException(session);
        }
        if (rolesRequired && !isInRole) {
            throw new UnauthorizedException();
        }
        // If you get here, then all that was requested was an authenticated user, 
        // user doesn't need to be consented or to possess any specific role.
        return session;
    }
    
    /** Package-scoped to make available in unit tests. */
    String getSessionToken() {
        String session = request().getHeader(SESSION_TOKEN_HEADER);
        if (StringUtils.isNotBlank(session)) {
            return session;
        }
        if (bridgeConfig.getEnvironment() == Environment.LOCAL) {
            // Not sure why this is 
            Cookie sessionCookie = WebUtils.getCookie(request(), SESSION_TOKEN_HEADER);
            if (sessionCookie != null && StringUtils.isNotBlank(sessionCookie.getValue())) {
                Cookie cookie = makeSessionCookie(sessionCookie.getValue(), BRIDGE_SESSION_EXPIRE_IN_SECONDS);
                response().addCookie(cookie);
                return sessionCookie.getValue();
            }
        }
        return null;
    }
    
    void verifySupportedVersionOrThrowException(Study study) throws UnsupportedVersionException {
        ClientInfo clientInfo = BridgeUtils.getRequestContext().getCallerClientInfo();
        String osName = clientInfo.getOsName();
        Integer minVersionForOs = study.getMinSupportedAppVersions().get(osName);
        
        if (!clientInfo.isSupportedAppVersion(minVersionForOs)) {
            throw new UnsupportedVersionException(clientInfo);
        }
    }

    /**
     * Once we acquire a language for a user, we save it and use that language going forward. Changing their 
     * language in the host operating system will not change the language they are using (since changing the 
     * language might change their consent state). If they change their language by updating their UserProfile, 
     * then they may have to reconsent in the new language they are using for the study. Any warnings to 
     * that effect will need to be included in the application.
     */
    List<String> getLanguages(UserSession session) {
        StudyParticipant participant = session.getParticipant();
        if (!participant.getLanguages().isEmpty()) {
            return participant.getLanguages();
        }
        RequestContext reqContext = BridgeUtils.getRequestContext();
        List<String> languages = reqContext.getCallerLanguages();
        if (!languages.isEmpty()) {
            accountDao.editAccount(session.getStudyIdentifier(), session.getHealthCode(),
                    account -> account.setLanguages(languages));
            
            // TODO
            // Totally redundant because we're about to change this from all the call sites
            // of this method
            RequestContext.Builder builder = reqContext.toBuilder();
            builder.withCallerLanguages(languages);
            sessionUpdateService.updateLanguage(session, builder.build());
        }
        return languages;
    }

    CriteriaContext getCriteriaContext(StudyIdentifier studyId) {
        RequestContext reqContext = BridgeUtils.getRequestContext();
        return new CriteriaContext.Builder()
            .withStudyIdentifier(studyId)
            .withLanguages(reqContext.getCallerLanguages())
            .withClientInfo(reqContext.getCallerClientInfo())
            .build();
    }
    
    void updateRequestContext(UserSession session) {
        checkNotNull(session);
        
        RequestContext.Builder builder = BridgeUtils.getRequestContext().toBuilder();
        builder.withCallerLanguages(getLanguages(session));
        builder.withCallerDataGroups(session.getParticipant().getDataGroups());
        builder.withCallerSubstudies(session.getParticipant().getSubstudyIds());
        builder.withCallerStudyId(session.getStudyIdentifier());
        builder.withCallerUserId(session.getId());
        BridgeUtils.setRequestContext(builder.build());
    }
    
    protected @Nonnull <T> T parseJson(Class<? extends T> clazz) {
        try {
            return MAPPER.readValue(request().getInputStream(), clazz);
        } catch (Throwable ex) {
            if (Throwables.getRootCause(ex) instanceof InvalidEntityException) {
                throw (InvalidEntityException)Throwables.getRootCause(ex);
            }
            throw new InvalidEntityException("Error parsing JSON in request body: " + ex.getMessage());    
        }
    }
    
    /**
     * Retrieves the metrics object from the cache. Can be null if the metrics is not in the cache.
     */
    Metrics getMetrics() {
        return BridgeUtils.getRequestContext().getMetrics();
    }

    /** Writes the user's account ID, internal session ID, and study ID to the metrics. */
    protected void writeSessionInfoToMetrics(UserSession session) {
        Metrics metrics = getMetrics();
        if (metrics != null && session != null) {
            metrics.setSessionId(session.getInternalSessionToken());
            metrics.setUserId(session.getId());
            metrics.setStudy(session.getStudyIdentifier().getIdentifier());
        }
    }
    
    /** Combines metrics logging with the setting of the session token as a cookie in local
     * environments (useful for testing).
     */
    protected void setCookieAndRecordMetrics(UserSession session) {
        writeSessionInfoToMetrics(session);  
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withSignedInOn(DateUtils.getCurrentDateTime()).build();
        
        requestInfoService.updateRequestInfo(requestInfo);
        
        // only set cookie in local environment
        if (bridgeConfig.getEnvironment() == Environment.LOCAL) {
            String sessionToken = session.getSessionToken();
            Cookie cookie = makeSessionCookie(sessionToken, BRIDGE_SESSION_EXPIRE_IN_SECONDS);
            response().addCookie(cookie);
        }
    }
    
    protected RequestInfo.Builder getRequestInfoBuilder(UserSession session) {
        checkNotNull(session);
        
        RequestContext reqContext = BridgeUtils.getRequestContext();
        
        RequestInfo.Builder builder = new RequestInfo.Builder();
        // If any timestamps exist, retrieve and preserve them in the returned requestInfo
        RequestInfo requestInfo = requestInfoService.getRequestInfo(session.getId());
        if (requestInfo != null) {
            builder.copyOf(requestInfo);
        }
        builder.withUserId(session.getId());
        builder.withClientInfo(reqContext.getCallerClientInfo());
        builder.withUserAgent(request().getHeader(USER_AGENT));
        builder.withLanguages(getLanguages(session));
        builder.withUserDataGroups(session.getParticipant().getDataGroups());
        builder.withUserSubstudyIds(session.getParticipant().getSubstudyIds());
        builder.withTimeZone(session.getParticipant().getTimeZone());
        builder.withStudyIdentifier(session.getStudyIdentifier());
        return builder;
    }
    
    protected Cookie makeSessionCookie(String sessionToken, int expireInSeconds) {
        Cookie cookie = new Cookie(SESSION_TOKEN_HEADER, sessionToken);
        cookie.setMaxAge(expireInSeconds);
        cookie.setPath("/");
        cookie.setDomain("localhost");
        cookie.setHttpOnly(false);
        cookie.setSecure(false);
        return cookie;
    }    
}
