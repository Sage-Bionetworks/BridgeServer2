package org.sagebionetworks.bridge.spring.controllers;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.BridgeConstants.SESSION_TOKEN_HEADER;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.springframework.http.HttpHeaders.USER_AGENT;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.spring.util.HttpUtil;
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
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.SponsorService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.time.DateUtils;

public abstract class BaseController {

    private static final Roles[] ADMIN_ROLE_ARRAY = ADMINISTRATIVE_ROLES.toArray(new Roles[] {});
    
    /**
     * The attribute key in request() for Filters to catch UserSession if it
     * exists.
     */
    public final static String USER_SESSION_FLAG = "CreatedUserSession";
    
    @FunctionalInterface
    private static interface ExceptionThrowingSupplier<T> {
        T get() throws Throwable;
    }
    
    protected final static ObjectMapper MAPPER = BridgeObjectMapper.get();

    CacheProvider cacheProvider;
    
    BridgeConfig bridgeConfig;

    AccountService accountService;

    AppService appService;

    AuthenticationService authenticationService;
    
    SessionUpdateService sessionUpdateService;
    
    RequestInfoService requestInfoService;
    
    SponsorService sponsorService;
    
    @Autowired
    final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
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
    
    @Autowired
    final void setSponsorService(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
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
        // Raise a "flag" in the request to let MetricsFilter record the metrics.
        request().setAttribute(USER_SESSION_FLAG, session);
        return session;
    }

    /**
     * Retrieve a user's session using the Bridge-Session header, throwing an exception if the session does
     * not have an administrative role (a role that is assigned to users).
     */
    UserSession getAdministrativeSession() throws NotAuthenticatedException, ConsentRequiredException, UnsupportedVersionException {
        return getAuthenticatedSession(false, ADMIN_ROLE_ARRAY);
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
        
        getLanguages(session);
        RequestContext reqContext = RequestContext.updateFromSession(session, sponsorService);
        
        // Sessions are locked to an IP address if (a) it is enabled in the app for unprivileged participant accounts
        // or (b) always for privileged accounts.
        App app = appService.getApp(session.getAppId());
        Set<Roles> userRoles = session.getParticipant().getRoles();
        boolean userHasRoles = !userRoles.isEmpty();
        if (app.isParticipantIpLockingEnabled() || userHasRoles) {
            String sessionIpAddress = session.getIpAddress();
            String requestIpAddress = reqContext.getCallerIpAddress();
            if (!Objects.equals(sessionIpAddress, requestIpAddress)) {
                throw new NotAuthenticatedException();
            }
        }

        // Any method that can throw a 412 can also throw a 410 (min app version not met).
        if (consentRequired) {
            verifySupportedVersionOrThrowException(app);
        }

        // if there are roles, they are required
        boolean rolesRequired = (roles != null && roles.length > 0); 
        boolean isInRole = (rolesRequired) ? session.isInRole(ImmutableSet.copyOf(roles)) : false;
        
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
                Cookie cookie = HttpUtil.makeSessionCookie(sessionCookie.getValue(), BRIDGE_SESSION_EXPIRE_IN_SECONDS);
                response().addCookie(cookie);
                return sessionCookie.getValue();
            }
        }
        return null;
    }
    
    void verifySupportedVersionOrThrowException(App app) throws UnsupportedVersionException {
        ClientInfo clientInfo = RequestContext.get().getCallerClientInfo();
        String osName = clientInfo.getOsName();
        Integer minVersionForOs = app.getMinSupportedAppVersions().get(osName);
        
        if (!clientInfo.isSupportedAppVersion(minVersionForOs)) {
            throw new UnsupportedVersionException(clientInfo);
        }
    }

    /**
     * Once we acquire a language for a user, we save it and use that language going forward. Changing their 
     * language in the host operating system will not change the language they are using (since changing the 
     * language might change their consent state). If they change their language by updating their UserProfile, 
     * then they may have to reconsent in the new language they are using for the app. Any warnings to 
     * that effect will need to be included in the application.
     */
    List<String> getLanguages(UserSession session) {
        StudyParticipant participant = session.getParticipant();
        if (!participant.getLanguages().isEmpty()) {
            return participant.getLanguages();
        }
        RequestContext reqContext = RequestContext.get();
        List<String> languages = reqContext.getCallerLanguages();
        if (!languages.isEmpty()) {
            accountService.editAccount(session.getAppId(), session.getHealthCode(),
                    account -> account.setLanguages(languages));

            CriteriaContext newContext = new CriteriaContext.Builder()
                .withLanguages(languages)
                .withClientInfo(reqContext.getCallerClientInfo())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withUserDataGroups(session.getParticipant().getDataGroups())
                .withUserStudyIds(session.getParticipant().getStudyIds())
                .withAppId(session.getAppId())
                .build();

            sessionUpdateService.updateLanguage(session, newContext);
        }
        return languages;
    }

    CriteriaContext getCriteriaContext(String appId) {
        RequestContext reqContext = RequestContext.get();
        return new CriteriaContext.Builder()
            .withAppId(appId)
            .withLanguages(reqContext.getCallerLanguages())
            .withClientInfo(reqContext.getCallerClientInfo())
            .build();
    }
    
    CriteriaContext getCriteriaContext(UserSession session) {
        checkNotNull(session);
        
        RequestContext reqContext = RequestContext.get();
        return new CriteriaContext.Builder()
            .withLanguages(getLanguages(session))
            .withClientInfo(reqContext.getCallerClientInfo())
            .withHealthCode(session.getHealthCode())
            .withUserId(session.getId())
            .withUserDataGroups(session.getParticipant().getDataGroups())
            .withUserStudyIds(session.getParticipant().getStudyIds())
            .withAppId(session.getAppId())
            .build();
    }
    
    protected @Nonnull <T> T parseJson(TypeReference<? extends T> clazz) {
        return parseWithExceptionConversion(() -> MAPPER.readValue(request().getInputStream(), clazz));
    }

    protected @Nonnull <T> T parseJson(Class<? extends T> clazz) {
        return parseWithExceptionConversion(() -> MAPPER.readValue(request().getInputStream(), clazz));
    }
    
    protected @Nonnull <T> T parseJson(JsonNode node, Class<? extends T> clazz) {
        return parseWithExceptionConversion(() -> MAPPER.treeToValue(node, clazz));
    }
    
    private @Nonnull <T> T parseWithExceptionConversion(ExceptionThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ex) {
            throw BridgeUtils.convertParsingError(ex);
        }
    }
    
    /**
     * Retrieves the metrics object from the cache. Can be null if the metrics is not in the cache.
     */
    Metrics getMetrics() {
        return RequestContext.get().getMetrics();
    }

    /**
     * Updates the request info from the given session. Also sets the USER_SESSION_FLAG
     * attribute in request() so that Filters can get the UserSession.
     *
     * @param session the given UserSession. If it is null, then do nothing.
     */
    protected void updateRequestInfoFromSession(UserSession session) {
        if (session != null) {
            RequestInfo requestInfo = getRequestInfoBuilder(session)
                    .withSignedInOn(DateUtils.getCurrentDateTime()).build();

            requestInfoService.updateRequestInfo(requestInfo);

            request().setAttribute(USER_SESSION_FLAG, session);
        }
    }
    
    protected RequestInfo.Builder getRequestInfoBuilder(UserSession session) {
        checkNotNull(session);
        
        RequestContext reqContext = RequestContext.get();
        
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
        builder.withUserStudyIds(session.getParticipant().getStudyIds());
        builder.withTimeZone(session.getParticipant().getTimeZone());
        builder.withAppId(session.getAppId());
        return builder;
    }
}
