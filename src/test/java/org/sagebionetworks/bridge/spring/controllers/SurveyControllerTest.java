package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_VIEW_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.CONSENTED_STATUS_MAP;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.TestUtils.randomName;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestSurvey;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.SurveyService;

public class SurveyControllerTest extends Mockito {

    private static final boolean CONSENTED = true;
    private static final boolean UNCONSENTED = false;
    private static final String SECONDSTUDY_STUDY_ID = "secondstudy";
    private static final String SURVEY_GUID = "bbb";
    private static final DateTime CREATED_ON = DateTime.now();
    private static final Long SURVEY_VERSION = 3L;
    private static final GuidCreatedOnVersionHolder KEYS = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, CREATED_ON.getMillis());
    
    @Spy
    @InjectMocks
    SurveyController controller;
    
    @Mock
    SurveyService mockSurveyService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    ViewCache viewCache;
    
    Map<CacheKey,String> cacheMap;
    
    UserSession session;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        // Finish mocking this in each test?
        // Dummy this out so it works and we can forget about it as a dependency
        cacheMap = new HashMap<>();
        viewCache = new ViewCache();
        viewCache.setObjectMapper(BridgeObjectMapper.get());
        viewCache.setCachePeriod(BRIDGE_VIEW_EXPIRE_IN_SECONDS);
        
        when(mockCacheProvider.getObject(any(), eq(String.class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                CacheKey key = invocation.getArgument(0);
                return cacheMap.get(key);
            }
        });
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                CacheKey key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                cacheMap.put(key, value);
                return null;
            }
        }).when(mockCacheProvider).setObject(any(), anyString(), anyInt());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                CacheKey key = invocation.getArgument(0);
                cacheMap.remove(key);
                return null;
            }
        }).when(mockCacheProvider).removeObject(any());
        viewCache.setCacheProvider(mockCacheProvider);
        
        App app = App.create();
        doReturn(app).when(mockAppService).getApp(any(String.class));
        controller.setViewCache(viewCache);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    private void setupContext(String appId, boolean hasConsented, Roles role) throws Exception {
        // Create a participant (with a role, if given)
        StudyParticipant.Builder builder = new StudyParticipant.Builder().withHealthCode("BBB");
        if (role != null) {
            builder.withRoles(ImmutableSet.of(role)).build();
        }
        StudyParticipant participant = builder.build();

        // Set up a session that is returned as if the user is already signed in.
        session = new UserSession(participant);
        session.setAppId(appId);
        session.setAuthenticated(true);
        
        // ... and setup session to report user consented, if needed.
        if (hasConsented) {
            session.setConsentStatuses(CONSENTED_STATUS_MAP);
        }
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SurveyController.class);
        assertGet(SurveyController.class, "getAllSurveysMostRecentVersion");
        assertGet(SurveyController.class, "getAllSurveysMostRecentlyPublishedVersion");
        assertGet(SurveyController.class, "getAllSurveysMostRecentlyPublishedVersionForApp");
        assertGet(SurveyController.class, "getSurveyMostRecentlyPublishedVersionForUser");
        assertGet(SurveyController.class, "getSurvey");
        assertGet(SurveyController.class, "getSurveyForUser");
        assertGet(SurveyController.class, "getSurveyMostRecentVersion");
        assertGet(SurveyController.class, "getSurveyMostRecentlyPublishedVersion");
        assertDelete(SurveyController.class, "deleteSurvey");
        assertGet(SurveyController.class, "getSurveyAllVersions");
        assertCreate(SurveyController.class, "createSurvey");
        assertCreate(SurveyController.class, "versionSurvey");
        assertPost(SurveyController.class, "updateSurvey");
        assertPost(SurveyController.class, "publishSurvey");
    }
    
    @Test
    public void verifyViewCacheIsWorking() throws Exception {
        setupContext(TEST_APP_ID, CONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        when(mockSurveyService.getSurveyMostRecentlyPublishedVersion(any(String.class), anyString(), eq(true)))
                .thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        
        verify(mockSurveyService, times(1)).getSurveyMostRecentlyPublishedVersion(TEST_APP_ID, SURVEY_GUID, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getAllSurveysMostRecentVersionDoNotIncludeDeleted() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        when(mockSurveyService.getAllSurveysMostRecentVersion(TEST_APP_ID, false)).thenReturn(getSurveys(3, false));
        
        controller.getAllSurveysMostRecentVersion(false);
        
        verify(mockSurveyService).getAllSurveysMostRecentVersion(TEST_APP_ID, false);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getAllSurveysMostRecentVersionIncludeDeleted() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
        when(mockSurveyService.getAllSurveysMostRecentVersion(TEST_APP_ID, true)).thenReturn(getSurveys(3, false));
        
        controller.getAllSurveysMostRecentVersion(true);
        
        verify(mockSurveyService).getAllSurveysMostRecentVersion(TEST_APP_ID, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getAllSurveysMostRecentlyPublishedVersionDoNotIncludeDeleted() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_APP_ID, false)).thenReturn(getSurveys(2, false));
        
        controller.getAllSurveysMostRecentlyPublishedVersion(false);
        
        verify(mockSurveyService).getAllSurveysMostRecentlyPublishedVersion(TEST_APP_ID, false);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getAllSurveysMostRecentlyPublishedVersionIncludeDeleted() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_APP_ID, true)).thenReturn(getSurveys(2, false));
        
        controller.getAllSurveysMostRecentlyPublishedVersion(true);
        
        verify(mockSurveyService).getAllSurveysMostRecentlyPublishedVersion(TEST_APP_ID, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getAllSurveysMostRecentlyPublishedVersionForStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, UNCONSENTED, WORKER);
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);
        // make surveys
        List<Survey> surveyList = getSurveys(2, false);
        surveyList.get(0).setGuid("survey-0");
        surveyList.get(1).setGuid("survey-1");
        when(mockSurveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_APP_ID, false)).thenReturn(surveyList);

        // execute and validate
        ResourceList<Survey> result = controller.getAllSurveysMostRecentlyPublishedVersionForApp(TEST_APP_ID, false);
        
        List<Survey> resultSurveyList = result.getItems();
        assertEquals(resultSurveyList.size(), 2);
        assertEquals(resultSurveyList.get(0).getGuid(), "survey-0");
        assertEquals(resultSurveyList.get(1).getGuid(), "survey-1");
    }

    @Test
    public void getSurveyForUser() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getSessionEitherConsentedOrInRole(WORKER, DEVELOPER);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, true, true)).thenReturn(getSurvey(false));
        
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, true, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getSurveyMostRecentlyPublishedVersionForUser() throws Exception {
        setupContext(TEST_APP_ID, CONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        when(mockSurveyService.getSurveyMostRecentlyPublishedVersion(TEST_APP_ID, SURVEY_GUID, true)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        
        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(TEST_APP_ID, SURVEY_GUID, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getSurvey() throws Exception {
        setupContext(TEST_APP_ID, CONSENTED, DEVELOPER);
        doReturn(session).when(controller).getSessionEitherConsentedOrInRole(WORKER, DEVELOPER);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, true, true)).thenReturn(getSurvey(false));
        
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, true, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getSurveyForWorker() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, WORKER);
        doReturn(session).when(controller).getSessionEitherConsentedOrInRole(WORKER, DEVELOPER);
        // make survey
        Survey survey = getSurvey(false);
        survey.setGuid("test-survey");
        when(mockSurveyService.getSurvey(null, KEYS, true, true)).thenReturn(survey);

        // execute and validate
        String result = controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        Survey resultSurvey = BridgeObjectMapper.get().readValue(result, Survey.class);
        assertEquals("test-survey", resultSurvey.getGuid());
    }

    @Test
    public void getSurveyMostRecentVersion() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.getSurveyMostRecentVersion(TEST_APP_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentVersion(SURVEY_GUID);

        verify(mockSurveyService).getSurveyMostRecentVersion(TEST_APP_ID, SURVEY_GUID);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getSurveyMostRecentlyPublishedVersion() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getSessionEitherConsentedOrInRole(DEVELOPER);
        when(mockSurveyService.getSurveyMostRecentlyPublishedVersion(TEST_APP_ID, SURVEY_GUID, true)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersion(SURVEY_GUID);

        verify(mockSurveyService).getSurveyMostRecentlyPublishedVersion(TEST_APP_ID, SURVEY_GUID, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void developerCanLogicallyDelete() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, false, false)).thenReturn(survey);
        
        StatusMessage result = controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), false);
        assertEquals(result, SurveyController.DELETED_MSG);
        
        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, false, false);
        verify(mockSurveyService).deleteSurvey(TEST_APP_ID, survey);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void adminCanLogicallyDelete() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, false, false)).thenReturn(survey);
        
        StatusMessage result = controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), false);
        assertEquals(result, SurveyController.DELETED_MSG);
        
        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, false, false);
        verify(mockSurveyService).deleteSurvey(TEST_APP_ID, survey);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void workerCannotDelete() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, WORKER);
        doReturn(session).when(controller).getSessionIfItExists();
        controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), false);
    }

    @Test
    public void deleteSurveyAllowedForDeveloper() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, false, false))
                .thenReturn(survey);
        
        StatusMessage result = controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), false);
        assertEquals(result, SurveyController.DELETED_MSG);

        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, false, false);
        verify(mockSurveyService).deleteSurvey(TEST_APP_ID, survey);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void physicalDeleteOfSurveyNotAllowedForDeveloper() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, false, false)).thenReturn(survey);
        
        StatusMessage result = controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), true);
        assertEquals(result, SurveyController.DELETED_MSG);
        
        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, false, false);
        verify(mockSurveyService).deleteSurvey(TEST_APP_ID, survey);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void physicalDeleteAllowedForAdmin() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, false, false)).thenReturn(survey);
        
        StatusMessage result = controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), true);
        assertEquals(result, SurveyController.DELETED_MSG);
        
        verify(mockSurveyService).getSurvey(TEST_APP_ID, KEYS, false, false);
        verify(mockSurveyService).deleteSurveyPermanently(TEST_APP_ID, survey);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteSurveyThrowsGoodExceptionIfSurveyDoesntExist() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, false, false)).thenReturn(null);
        
        controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), false);
    }

    @Test
    public void getSurveyAllVersionsExcludeDeleted() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.getSurveyAllVersions(TEST_APP_ID, SURVEY_GUID, false)).thenReturn(getSurveys(3, false));
        
        ResourceList<Survey> result = controller.getSurveyAllVersions(SURVEY_GUID, false);
        assertEquals(result.getItems().size(), 3);
        
        verify(mockSurveyService).getSurveyAllVersions(TEST_APP_ID, SURVEY_GUID, false);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void getSurveyAllVersionsIncludeDeleted() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.getSurveyAllVersions(TEST_APP_ID, SURVEY_GUID, true)).thenReturn(getSurveys(3, false));
        
        ResourceList<Survey> result = controller.getSurveyAllVersions(SURVEY_GUID, true);
        assertEquals(result.getItems().size(), 3);
        
        verify(mockSurveyService).getSurveyAllVersions(TEST_APP_ID, SURVEY_GUID, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void createSurvey() throws Exception {
        Survey survey = getSurvey(true);
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        mockRequestBody(mockRequest, survey);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.createSurvey(any(Survey.class))).thenReturn(survey);
        
        GuidCreatedOnVersionHolder result = controller.createSurvey();
        assertEquals(result.getGuid(), SURVEY_GUID);
        assertEquals(result.getCreatedOn(), CREATED_ON.getMillis());
        assertEquals(result.getVersion(), SURVEY_VERSION);

        verify(mockSurveyService).createSurvey(any(Survey.class));
        verifyNoMoreInteractions(mockSurveyService);
    }
    
    // There's no such thing as not being able to create a study from another study. If
    // you create a survey, it's in your study.

    @Test
    public void versionSurvey() throws Exception {
        Survey survey = getSurvey(false);
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        mockRequestBody(mockRequest, survey);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.versionSurvey(eq(TEST_APP_ID), any(GuidCreatedOnVersionHolder.class))).thenReturn(survey);
        
        GuidCreatedOnVersionHolder result = controller.versionSurvey(SURVEY_GUID, CREATED_ON.toString());
        assertEquals(result.getGuid(), SURVEY_GUID);
        assertEquals(result.getCreatedOn(), CREATED_ON.getMillis());
        assertEquals(result.getVersion(), SURVEY_VERSION);
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, CREATED_ON.getMillis());
        
        verify(mockSurveyService).versionSurvey(TEST_APP_ID, keys);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void updateSurvey() throws Exception {
        Survey survey = getSurvey(false);
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        mockRequestBody(mockRequest, survey);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.updateSurvey(eq(TEST_APP_ID), any(Survey.class))).thenReturn(survey);
        
        controller.updateSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        verify(mockSurveyService).updateSurvey(eq(TEST_APP_ID), any(Survey.class));
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void publishSurveyNewSchemaRev() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Survey survey = getSurvey(false);
        when(mockSurveyService.publishSurvey(eq(TEST_APP_ID), eq(KEYS), eq(true))).thenReturn(survey);

        controller.publishSurvey(SURVEY_GUID, CREATED_ON.toString(), true);
        
        verify(mockSurveyService).publishSurvey(TEST_APP_ID, KEYS, true);
        verifyNoMoreInteractions(mockSurveyService);
    }

    @Test
    public void adminRejectedAsUnauthorized() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, ADMIN);
        doReturn(session).when(controller).getSessionIfItExists();
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, true, true)).thenReturn(survey);
        
        try {
            controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Exception should have been thrown.");
        } catch(UnauthorizedException e) {
            verifyNoMoreInteractions(mockSurveyService);
        }
    }

    @Test
    public void studyParticipantRejectedAsNotConsented() throws Exception {
        setupContext(TEST_APP_ID, UNCONSENTED, null);
        doReturn(session).when(controller).getSessionIfItExists();
        Survey survey = getSurvey(false);
        when(mockSurveyService.getSurvey(TEST_APP_ID, KEYS, true, true)).thenReturn(survey);
        
        try {
            controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Exception should have been thrown.");
        } catch(ConsentRequiredException e) {
            verifyNoMoreInteractions(mockSurveyService);
        }
    }    
    
    @Test
    public void deleteSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> {
            doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
            controller.deleteSurvey(guid, dateString, false);
        }, 2);
    }
    
    @Test
    public void versionSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.versionSurvey(guid, dateString));
    }
    
    @Test
    public void updateSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.updateSurvey(guid, dateString));
    }
    
    @Test
    public void publishSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.publishSurvey(guid, dateString, false));
    }
    
    @FunctionalInterface
    public interface ExecuteSurvey {
        public void execute(String guid, String dateString) throws Exception;    
    }
   
    private void assertCacheIsCleared(ExecuteSurvey executeSurvey) throws Exception {
        assertCacheIsCleared(executeSurvey, 1);
    }
    
    private void assertCacheIsCleared(ExecuteSurvey executeSurvey, int getCount) throws Exception {
        // Setup the cache to return content and verify the cache returns content
        Survey survey = new DynamoSurvey();
        survey.setAppId(TEST_APP_ID);
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(CREATED_ON.getMillis());
        
        setupContext(TEST_APP_ID, false, DEVELOPER);
        TestUtils.mockRequestBody(mockRequest, survey);
        doReturn(session).when(controller).getSessionEitherConsentedOrInRole(WORKER, DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockSurveyService.getSurvey(eq(TEST_APP_ID), any(), anyBoolean(), anyBoolean())).thenReturn(survey);
        
        viewCache.getView(viewCache.getCacheKey(
                Survey.class, SURVEY_GUID, CREATED_ON.toString(), TEST_APP_ID), () -> { return survey; });
        
        // Verify this call hits the cache not the mockSurveyService
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        verifyNoMoreInteractions(mockSurveyService);

        // Now mock the mockSurveyService because the *next* call (publish/delete/etc) will require it. The 
        // calls under test do not reference the cache, they clear it.
        when(mockSurveyService.publishSurvey(any(), any(), anyBoolean())).thenReturn(survey);
        when(mockSurveyService.versionSurvey(eq(TEST_APP_ID), any())).thenReturn(survey);
        when(mockSurveyService.updateSurvey(eq(TEST_APP_ID), any())).thenReturn(survey);
        
        // execute the test method, this should delete the cache
        executeSurvey.execute(SURVEY_GUID, CREATED_ON.toString());
        
        // This call now hits the mockSurveyService, not the cache, for what should be one hit
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        verify(mockSurveyService, times(getCount)).getSurvey(any(), any(), anyBoolean(), anyBoolean());
    }

    private Survey getSurvey(boolean makeNew) {
        Survey survey = new TestSurvey(SurveyControllerTest.class, makeNew);
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(CREATED_ON.getMillis());
        survey.setName(randomName(SurveyControllerTest.class));
        survey.setVersion(SURVEY_VERSION);
        return survey;
    }
    
    private List<Survey> getSurveys(int count, boolean makeNew) {
        List<Survey> lists = Lists.newArrayListWithCapacity(count);
        for (int i=0; i < count; i++) {
            lists.add(getSurvey(makeNew));
        }
        return lists;
    }
}