package org.sagebionetworks.bridge.services;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;

import java.util.List;
import java.util.Optional;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.files.FileReference;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class AppConfigServiceTest {
    
    private static final List<AppConfig>  RESULTS = Lists.newArrayList();
    private static final String  GUID = BridgeUtils.generateGuid();
    private static final DateTime TIMESTAMP = DateTime.now();
    private static final long EARLIER_TIMESTAMP = DateTime.now().minusDays(1).getMillis();
    private static final long LATER_TIMESTAMP = DateTime.now().getMillis();
    private static final List<SurveyReference> SURVEY_REF_LIST = ImmutableList
            .of(new SurveyReference(null, "guid", DateTime.now()));
    private static final List<SchemaReference> SCHEMA_REF_LIST = ImmutableList.of(new SchemaReference("id", 3));
    private static final List<ConfigReference> CONFIG_REF_LIST = ImmutableList.of(new ConfigReference("id", 1L));
    private static final List<FileReference> FILE_REF_LIST = ImmutableList.of(new FileReference(GUID, TIMESTAMP));
    private static final GuidCreatedOnVersionHolder SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(SURVEY_REF_LIST.get(0));
    
    @Mock
    private AppConfigDao mockDao;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AppConfigElementService mockAppConfigElementService;
    
    @Mock
    private SubstudyService mockSubstudyService;
    
    @Mock
    private SurveyService mockSurveyService;
    
    @Mock
    private UploadSchemaService mockSchemaService;
    
    @Mock
    private FileService mockFileService;
    
    @Mock
    private UploadSchema mockUploadSchema;
    
    @Mock
    private AppConfigElement mockConfigElement;
    
    @Mock
    private Survey mockSurvey;
    
    @Mock
    private ReferenceResolver mockReferenceResolver;
    
    @Captor
    private ArgumentCaptor<AppConfig> appConfigCaptor;
    
    @Captor
    private ArgumentCaptor<SurveyReference> surveyRefCaptor;
    
    @Captor
    private ArgumentCaptor<SchemaReference> schemaRefCaptor;
    
    @Spy
    @InjectMocks
    private AppConfigService service;

    private Study study;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getCurrentTimestamp()).thenReturn(TIMESTAMP.getMillis());
        when(service.getGUID()).thenReturn(GUID);
        
        AppConfig savedAppConfig = AppConfig.create();
        savedAppConfig.setLabel("AppConfig");
        savedAppConfig.setGuid(GUID);
        savedAppConfig.setAppId(TEST_APP_ID);
        savedAppConfig.setCriteria(Criteria.create());
        savedAppConfig.setCreatedOn(TIMESTAMP.getMillis());
        savedAppConfig.setModifiedOn(TIMESTAMP.getMillis());
        when(mockDao.getAppConfig(TEST_APP_ID, GUID)).thenReturn(savedAppConfig);
        when(mockDao.updateAppConfig(any())).thenReturn(savedAppConfig);
     
        when(mockSubstudyService.getSubstudyIds(TEST_APP_ID)).thenReturn(TestConstants.USER_SUBSTUDY_IDS);
        
        study = Study.create();
        study.setIdentifier(TEST_APP_ID);
    }
    
    @AfterMethod
    public void after() {
        RESULTS.clear();
    }
    
    private AppConfig setupConfigsForUser() {
        Criteria criteria1 = Criteria.create();
        criteria1.setMinAppVersion(OperatingSystem.ANDROID, 0);
        criteria1.setMaxAppVersion(OperatingSystem.ANDROID, 6);
        
        AppConfig appConfig1 = AppConfig.create();
        appConfig1.setLabel("AppConfig1");
        appConfig1.setCriteria(criteria1);
        appConfig1.setCreatedOn(LATER_TIMESTAMP);
        RESULTS.add(appConfig1);
        
        Criteria criteria2 = Criteria.create();
        criteria2.setMinAppVersion(OperatingSystem.ANDROID, 6);
        criteria2.setMaxAppVersion(OperatingSystem.ANDROID, 20);
        
        AppConfig appConfig2 = AppConfig.create();
        appConfig2.setLabel("AppConfig2");
        appConfig2.setCriteria(criteria2);
        appConfig2.setCreatedOn(EARLIER_TIMESTAMP);
        // Add some references to verify we call the resolver
        appConfig2.setSurveyReferences(SURVEY_REF_LIST);
        appConfig2.setSchemaReferences(SCHEMA_REF_LIST);
        RESULTS.add(appConfig2);
        
        when(mockDao.getAppConfigs(TEST_APP_ID, false)).thenReturn(RESULTS);
        return appConfig2;
    }
    
    private AppConfig setupAppConfig() {
        AppConfig config = AppConfig.create();
        config.setLabel("AppConfig");
        config.setCriteria(Criteria.create());
        return config;
    }
    
    @Test
    public void getAppConfigs() {
        when(mockDao.getAppConfigs(TEST_APP_ID, false)).thenReturn(RESULTS);
        
        List<AppConfig> results = service.getAppConfigs(TEST_APP_ID, false);
        assertEquals(results, RESULTS);
        
        verify(mockDao).getAppConfigs(TEST_APP_ID, false);
    }
    
    @Test
    public void getAppConfig() {
        AppConfig returnValue = service.getAppConfig(TEST_APP_ID, GUID);
        assertNotNull(returnValue);
        
        verify(mockDao).getAppConfig(TEST_APP_ID, GUID);
    }
    
    @Test
    public void getAppConfigForUser() {
        Survey survey = Survey.create();
        survey.setIdentifier("theIdentifier");
        survey.setGuid(SURVEY_REF_LIST.get(0).getGuid());
        survey.setCreatedOn(SURVEY_REF_LIST.get(0).getCreatedOn().getMillis());
        when(mockSurveyService.getSurvey(TEST_APP_ID, SURVEY_KEY, false, false)).thenReturn(survey);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withAppId(TEST_APP_ID).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        
        AppConfig match = service.getAppConfigForUser(context, true);
        assertEquals(match, appConfig2);
        
        // Verify that we called the resolver on this as well
        assertEquals(match.getSurveyReferences().get(0).getIdentifier(), "theIdentifier");
    }
    
    @Test
    public void getAppConfigForUserIncludesElements() {
        JsonNode clientData1 = TestUtils.getClientData();
        AppConfigElement element1 = AppConfigElement.create();
        element1.setId("id1");
        element1.setRevision(1L);
        element1.setData(clientData1);
        
        JsonNode clientData2 = TestUtils.getOtherClientData();
        AppConfigElement element2 = AppConfigElement.create();
        element2.setId("id2");
        element2.setRevision(2L);
        element2.setData(clientData2);

        ConfigReference ref1 = new ConfigReference("id1", 1L);
        ConfigReference ref2 = new ConfigReference("id2", 2L);
        List<ConfigReference> refs = ImmutableList.of(ref1, ref2);
        
        when(mockAppConfigElementService.getElementRevision(TEST_APP_ID, "id1", 1L)).thenReturn(element1);
        when(mockAppConfigElementService.getElementRevision(TEST_APP_ID, "id2", 2L)).thenReturn(element2);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withAppId(TEST_APP_ID).build();
        
        AppConfig appConfig = setupConfigsForUser();
        appConfig.setSurveyReferences(null);
        appConfig.setSchemaReferences(null);
        appConfig.setConfigReferences(refs);
        
        AppConfig match = service.getAppConfigForUser(context, true);
        
        assertEquals(match.getConfigElements().size(), 2);
        assertEquals(match.getConfigElements().get("id1"), clientData1);
        assertEquals(match.getConfigElements().get("id2"), clientData2);
        
        // The references are still there. They list the versions being used
        assertEquals(match.getConfigReferences().size(), 2);
        assertEquals(match.getConfigReferences().get(0).getId(), "id1");
        assertEquals(match.getConfigReferences().get(0).getRevision(), new Long(1));
        assertEquals(match.getConfigReferences().get(1).getId(), "id2");
        assertEquals(match.getConfigReferences().get(1).getRevision(), new Long(2));
    }
    
    @Test
    public void getAppConfigForUserReferencingMissingElement() {
        JsonNode clientData2 = TestUtils.getOtherClientData();
        AppConfigElement element2 = AppConfigElement.create();
        element2.setId("id2");
        element2.setRevision(2L);
        element2.setData(clientData2);

        ConfigReference ref1 = new ConfigReference("id1", 1L);
        ConfigReference ref2 = new ConfigReference("id2", 2L);
        List<ConfigReference> refs = ImmutableList.of(ref1, ref2);
        
        when(mockAppConfigElementService.getElementRevision(TEST_APP_ID, "id1", 1L))
                .thenThrow(new EntityNotFoundException(AppConfigElement.class));
        when(mockAppConfigElementService.getElementRevision(TEST_APP_ID, "id2", 2L)).thenReturn(element2);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withAppId(TEST_APP_ID).build();
        
        AppConfig appConfig = setupConfigsForUser();
        appConfig.setGuid("abc-def");
        appConfig.setConfigReferences(refs);
        
        AppConfig match = service.getAppConfigForUser(context, true);
        
        assertEquals(match.getConfigElements().size(), 1);
        // id1 is not included but this does not prevent id2 from being included.
        assertNull(match.getConfigElements().get("id1"));
        assertEquals(match.getConfigElements().get("id2"), clientData2);
        
        verify(service).logError("AppConfig[guid=abc-def] references missing AppConfigElement[id=id1, revision=1]");
    }

    @Test
    public void getAppConfigForUserMatchesMultipleAppConfigs() throws Exception {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withAppId(TEST_APP_ID).build();
        
        AppConfig appConfig1 = AppConfig.create();
        appConfig1.setLabel("AppConfig1");
        appConfig1.setCriteria(Criteria.create());
        appConfig1.setCreatedOn(LATER_TIMESTAMP);
        RESULTS.add(appConfig1);
        
        AppConfig appConfig2 = AppConfig.create();
        appConfig2.setLabel("AppConfig2");
        appConfig2.setCriteria(Criteria.create());
        appConfig2.setCreatedOn(EARLIER_TIMESTAMP);
        RESULTS.add(appConfig2);
        
        when(mockDao.getAppConfigs(TEST_APP_ID, false)).thenReturn(RESULTS);
        
        AppConfig appConfig = service.getAppConfigForUser(context, false);

        assertEquals(appConfig, appConfig2);
    }
    
    // This should not actually ever happen. We're suppressing exceptions if the survey is missing.
    @Test
    public void getAppConfigForUserSurveyDoesNotExist() throws Exception {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withAppId(TEST_APP_ID).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        
        AppConfig match = service.getAppConfigForUser(context, true);
        assertEquals(match, appConfig2);
        
        assertEquals(match.getSurveyReferences().get(0), SURVEY_REF_LIST.get(0));        
    }
    
    @Test
    public void getAppConfigForUserSurveyIdentifierAlreadySet() throws Exception {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withAppId(TEST_APP_ID).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        appConfig2.setSurveyReferences(Lists.newArrayList(new SurveyReference("anIdentifier", "guid", DateTime.now())));
        
        AppConfig match = service.getAppConfigForUser(context, true);
        
        assertEquals(match.getSurveyReferences().get(0).getIdentifier(), "anIdentifier");
        verify(mockSurveyService, never()).getSurvey(eq(TEST_APP_ID), any(), eq(false), eq(true));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAppConfigForUserThrowsException() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/21 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withAppId(TEST_APP_ID).build();
        
        setupConfigsForUser();
        service.getAppConfigForUser(context, true);
    }
    
    @Test
    public void getAppCOnfigForUserReturnsNull() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/21 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withAppId(TEST_APP_ID).build();
        
        setupConfigsForUser();
        AppConfig result = service.getAppConfigForUser(context, false);
        assertNull(result);
    }

    @Test
    public void getAppConfigForUserReturnsOldestVersion() {
        Survey survey = Survey.create();
        survey.setIdentifier("theIdentifier");
        survey.setGuid(SURVEY_REF_LIST.get(0).getGuid());
        survey.setCreatedOn(SURVEY_REF_LIST.get(0).getCreatedOn().getMillis());
        when(mockSurveyService.getSurvey(TEST_APP_ID, SURVEY_KEY, false, false)).thenReturn(survey);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("iPhone/6 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withAppId(TEST_APP_ID).build();
        
        setupConfigsForUser();
        AppConfig appConfig = service.getAppConfigForUser(context, true);
        assertEquals(appConfig.getCreatedOn(), EARLIER_TIMESTAMP);
        assertEquals(appConfig.getSurveyReferences().get(0).getIdentifier(), "theIdentifier");
    }
    
    @Test
    public void createAppConfig() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);
        when(mockSchemaService.getUploadSchemaByIdAndRev(any(), any(), anyInt())).thenReturn(mockUploadSchema);
        when(mockSurveyService.getSurvey(any(), any(), anyBoolean(), anyBoolean())).thenReturn(mockSurvey);
        when(mockAppConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(mockConfigElement);
        when(mockFileService.getFileRevision(eq(GUID), any())).thenReturn(Optional.of(new FileRevision()));
        when(mockSurvey.isPublished()).thenReturn(true);
        
        AppConfig newConfig = setupAppConfig();
        newConfig.setClientData(TestUtils.getClientData());
        newConfig.setSurveyReferences(SURVEY_REF_LIST);
        newConfig.setSchemaReferences(SCHEMA_REF_LIST);
        newConfig.setConfigReferences(CONFIG_REF_LIST);
        newConfig.setFileReferences(FILE_REF_LIST);
        
        AppConfig returnValue = service.createAppConfig(TEST_APP_ID, newConfig);
        
        assertEquals(returnValue.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(returnValue.getModifiedOn(), TIMESTAMP.getMillis());
        assertEquals(returnValue.getGuid(), GUID);
        assertEquals(returnValue.getLabel(), newConfig.getLabel());
        assertEquals(returnValue.getAppId(), TEST_APP_ID);
        assertEquals(returnValue.getClientData(), TestUtils.getClientData());
        assertEquals(returnValue.getSurveyReferences(), SURVEY_REF_LIST);
        assertEquals(returnValue.getSchemaReferences(), SCHEMA_REF_LIST);
        assertEquals(returnValue.getConfigReferences(), CONFIG_REF_LIST);
        assertEquals(returnValue.getFileReferences(), FILE_REF_LIST);
        
        verify(mockDao).createAppConfig(appConfigCaptor.capture());
        
        AppConfig captured = appConfigCaptor.getValue();
        assertEquals(captured.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getModifiedOn(), TIMESTAMP.getMillis());
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getLabel(), newConfig.getLabel());
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getClientData().toString(), TestUtils.getClientData().toString());
        assertEquals(captured.getSurveyReferences(), SURVEY_REF_LIST);
        assertEquals(captured.getSchemaReferences(), SCHEMA_REF_LIST);
        assertEquals(captured.getConfigReferences(), CONFIG_REF_LIST);
        assertEquals(captured.getFileReferences(), FILE_REF_LIST);
        
        verify(mockSubstudyService).getSubstudyIds(TEST_APP_ID);
    }
    
    @Test
    public void updateAppConfig() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);

        AppConfig oldConfig = setupAppConfig();
        oldConfig.setCreatedOn(0);
        oldConfig.setModifiedOn(0);
        oldConfig.setGuid(GUID);
        AppConfig returnValue = service.updateAppConfig(TEST_APP_ID, oldConfig);
        
        assertEquals(returnValue.getCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(returnValue.getModifiedOn(), TIMESTAMP.getMillis());
        
        verify(mockDao).updateAppConfig(appConfigCaptor.capture());
        assertEquals(appConfigCaptor.getValue(), oldConfig);
        
        verify(mockSubstudyService).getSubstudyIds(TEST_APP_ID);

        assertEquals(oldConfig, returnValue);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createAppConfigValidates() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);
        
        service.createAppConfig(TEST_APP_ID, AppConfig.create());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateAppConfigValidates() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);
        
        AppConfig oldConfig = setupAppConfig();
        service.updateAppConfig(TEST_APP_ID, oldConfig);
    }
    
    @Test
    public void deleteAppConfig() {
        service.deleteAppConfig(TEST_APP_ID,  GUID);
        
        verify(mockDao).deleteAppConfig(TEST_APP_ID, GUID);
    }
    
    @Test
    public void deleteAppConfigPermanently() {
        service.deleteAppConfigPermanently(TEST_APP_ID, GUID);
        
        verify(mockDao).deleteAppConfigPermanently(TEST_APP_ID, GUID);
    }
}
