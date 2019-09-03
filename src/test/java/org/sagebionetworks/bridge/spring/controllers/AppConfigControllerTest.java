package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.UA;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AppConfigService;
import org.sagebionetworks.bridge.services.StudyService;

public class AppConfigControllerTest extends Mockito {
    
    private static final String GUID = "guid";
    private static final CacheKey CACHE_KEY = CacheKey.appConfigList(TEST_STUDY);
    
    @InjectMocks
    @Spy
    private AppConfigController controller;
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private AppConfigService mockService;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private ViewCache viewCache;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    private Study study;
    
    private AppConfig appConfig;
    
    private UserSession session;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        // With mock dependencies, the view cache just doesn't work (no cache hits), and tests that aren't
        // specifically verifying caching behavior pass.
        viewCache = new ViewCache();
        viewCache.setCacheProvider(mockCacheProvider);
        viewCache.setObjectMapper(BridgeObjectMapper.get());
        viewCache.setCachePeriod(100);
        controller.setViewCache(viewCache);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        appConfig = AppConfig.create();
        appConfig.setGuid(GUID);
        appConfig.setVersion(1L);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        session.setParticipant(new StudyParticipant.Builder()
                .withDataGroups(USER_DATA_GROUPS)
                .withLanguages(LANGUAGES)
                .withRoles(ImmutableSet.of(DEVELOPER))
                .withHealthCode(HEALTH_CODE)
                .build());
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AppConfigController.class);
        assertGet(AppConfigController.class, "getStudyAppConfig");
        assertGet(AppConfigController.class, "getAppConfigs");
        assertCreate(AppConfigController.class, "createAppConfig");
        assertGet(AppConfigController.class, "getAppConfig");
        assertPost(AppConfigController.class, "updateAppConfig");
        assertDelete(AppConfigController.class, "deleteAppConfig");
    }
    
    @Test
    public void getStudyAppConfig() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("en"))
                .withCallerClientInfo(ClientInfo.fromUserAgentCache(UA)).build());
        
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockService.getAppConfigForCaller()).thenReturn(Optional.of(appConfig));
        
        String string = controller.getStudyAppConfig("api");
        AppConfig returnedValue = BridgeObjectMapper.get().readValue(string, AppConfig.class);
        assertEquals(returnedValue, appConfig);
        
        verify(mockService).getAppConfigForCaller();
        
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void getAppConfigs() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        List<AppConfig> list = ImmutableList.of(AppConfig.create(), AppConfig.create());
        when(mockService.getAppConfigs(TEST_STUDY, false)).thenReturn(list);
        
        ResourceList<AppConfig> results = controller.getAppConfigs("false");
        assertEquals(2, results.getItems().size());
        assertFalse((Boolean)results.getRequestParams().get("includeDeleted"));
        verify(mockService).getAppConfigs(TEST_STUDY, false);
    }

    @Test
    public void getAppConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.getAppConfig(TEST_STUDY, GUID)).thenReturn(appConfig);
        
        AppConfig result = controller.getAppConfig(GUID);

        assertEquals(appConfig.getGuid(), result.getGuid());
        verify(mockService).getAppConfig(TEST_STUDY, GUID);
    }

    @Test
    public void createAppConfig() throws Exception {
        mockRequestBody(mockRequest, appConfig);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.createAppConfig(eq(TEST_STUDY), any())).thenReturn(appConfig);
        
        GuidVersionHolder result = controller.createAppConfig();
        
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), new Long(1));
        verify(mockService).createAppConfig(eq(TEST_STUDY), any());
    }
    
    @Test
    public void updateAppConfig() throws Exception {
        mockRequestBody(mockRequest, appConfig);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.updateAppConfig(eq(TEST_STUDY), any())).thenReturn(appConfig);
        
        GuidVersionHolder result = controller.updateAppConfig(GUID);
        
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), new Long(1));
        verify(mockService).updateAppConfig(eq(TEST_STUDY), any());
    }
    
    @Test
    public void deleteAppConfigDefault() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);

        StatusMessage message = controller.deleteAppConfig(GUID, null);
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }

    @Test
    public void deleteAppConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage message = controller.deleteAppConfig(GUID, "false");
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }

    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage message = controller.deleteAppConfig(GUID, "true");
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(DEVELOPER, ADMIN))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage message = controller.deleteAppConfig(GUID, "true");
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfigPermanently(TEST_STUDY, GUID);
    }

    @Test
    public void getStudyAppConfigAddsToCache() throws Exception {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("en"))
                .withCallerClientInfo(ClientInfo.fromUserAgentCache(UA)).build());
        mockRequestBody(mockRequest, appConfig);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockService.getAppConfigForCaller()).thenReturn(Optional.of(appConfig));
        
        controller.getStudyAppConfig(TEST_STUDY_IDENTIFIER);
        
        verify(mockCacheProvider).addCacheKeyToSet(CACHE_KEY, "26:iPhone OS:en:api:AppConfig:view");
    }

    @Test
    public void createAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.createAppConfig(any(), any())).thenReturn(appConfig);
        mockRequestBody(mockRequest, appConfig);
        
        controller.createAppConfig();
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }

    @Test
    public void updateAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.updateAppConfig(any(), any())).thenReturn(appConfig);
        mockRequestBody(mockRequest, appConfig);
        
        controller.updateAppConfig("guid");
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }

    @Test
    public void deleteAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        controller.deleteAppConfig("guid", null);
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }
}
