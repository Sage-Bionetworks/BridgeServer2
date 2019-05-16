package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

public class HealthDataControllerTest extends Mockito {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String CREATED_ON_STR = "2017-08-24T14:38:57.340+09:00";
    private static final DateTime CREATED_ON = DateTime.parse(CREATED_ON_STR);
    private static final String CREATED_ON_END_STR = "2017-08-24T19:03:02.757+09:00";
    private static final DateTime CREATED_ON_END = DateTime.parse(CREATED_ON_END_STR);
    private static final String HEALTH_CODE = "health-code";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();
    private static final StudyParticipant OTHER_PARTICIPANT = new StudyParticipant.Builder()
            .withHealthCode(HEALTH_CODE).build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .build();
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final String TEST_RECORD_ID = "record-to-update";
    private static final String USER_ID = "test-user";

    private static final Study STUDY;
    static {
        STUDY = Study.create();
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    private static final HealthDataRecord.ExporterStatus TEST_STATUS = HealthDataRecord.ExporterStatus.SUCCEEDED;
    private static final String TEST_STATUS_JSON = "{\n" +
            "   \"recordIds\":[\"record-to-update\"],\n" +
            "   \"synapseExporterStatus\":\"SUCCEEDED\"\n" +
            "}";

    @InjectMocks
    @Spy
    HealthDataController controller;

    @Mock
    CacheProvider mockCacheProvider;

    @Mock
    HealthDataService mockHealthDataService;

    @Mock
    ParticipantService mockParticipantService;

    @Mock
    StudyService mockStudyService;

    @Mock
    Metrics mockMetrics;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;

    @Captor
    ArgumentCaptor<RecordExportStatusRequest> requestArgumentCaptor;
    
    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // mock Metrics
        doReturn(mockMetrics).when(controller).getMetrics();

        // Mock services.
        when(mockParticipantService.getParticipant(same(STUDY), eq(USER_ID), anyBoolean())).thenReturn(OTHER_PARTICIPANT);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);

        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        mockSession.setParticipant(PARTICIPANT);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());

        // mock RequestInfo
        doReturn(new RequestInfo.Builder()).when(controller).getRequestInfoBuilder(mockSession);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(HealthDataController.class);
        assertGet(HealthDataController.class, "getRecordsByCreatedOn");
        assertCreate(HealthDataController.class, "submitHealthData");
        assertCreate(HealthDataController.class, "submitHealthDataForParticipant");
        assertPost(HealthDataController.class, "updateRecordsStatus");
    }
    
    @Test
    public void getRecordsByHealthCodeCreatedOn() throws Exception {
        // Mock health data service to return a couple dummy health data records.
        HealthDataRecord record1 = HealthDataRecord.create();
        record1.setId(TEST_RECORD_ID + "1");
        record1.setHealthCode(HEALTH_CODE);

        HealthDataRecord record2 = HealthDataRecord.create();
        record2.setId(TEST_RECORD_ID + "2");
        record2.setHealthCode(HEALTH_CODE);

        when(mockHealthDataService.getRecordsByHealthCodeCreatedOn(HEALTH_CODE, CREATED_ON, CREATED_ON_END)).thenReturn(
                ImmutableList.of(record1, record2));

        // Execute and verify.
        String json = controller.getRecordsByCreatedOn(CREATED_ON_STR, CREATED_ON_END_STR);

        DateTimeRangeResourceList<HealthDataRecord> recordResourceList = BridgeObjectMapper.get().readValue(
                json, HealthDataController.RECORD_RESOURCE_LIST_TYPE_REF);
        assertEquals(recordResourceList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_STR);
        assertEquals(recordResourceList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STR);

        // Note that we filter out health code.
        List<HealthDataRecord> recordList = recordResourceList.getItems();
        assertEquals(recordList.size(), 2);

        assertEquals(TEST_RECORD_ID + "1", recordList.get(0).getId());
        assertNull(recordList.get(0).getHealthCode());

        assertEquals(TEST_RECORD_ID + "2", recordList.get(1).getId());
        assertNull(recordList.get(1).getHealthCode());
    }

    @Test
    public void submitHealthData() throws Exception {
        // mock request JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"" + CREATED_ON_STR + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"data\":{\n" +
                "       \"foo\":\"foo-value\",\n" +
                "       \"bar\":42\n" +
                "   }\n" +
                "}";
        mockRequestBody(mockRequest, jsonText);

        // mock back-end call - We only care about record ID. Also add Health Code to make sure it's being filtered.
        HealthDataRecord svcRecord = HealthDataRecord.create();
        svcRecord.setId(TEST_RECORD_ID);
        svcRecord.setHealthCode(HEALTH_CODE);
        when(mockHealthDataService.submitHealthData(any(), any(), any())).thenReturn(svcRecord);

        // execute and validate - Just check record ID. Health Code is filtered out.
        String result = controller.submitHealthData();
        HealthDataRecord controllerRecord = BridgeObjectMapper.get().readValue(result, HealthDataRecord.class);
        assertEquals(controllerRecord.getId(), TEST_RECORD_ID);
        assertNull(controllerRecord.getHealthCode());

        // validate call to healthDataService
        ArgumentCaptor<HealthDataSubmission> submissionCaptor = ArgumentCaptor.forClass(HealthDataSubmission.class);
        verify(mockHealthDataService).submitHealthData(eq(TEST_STUDY), same(PARTICIPANT), submissionCaptor.capture());

        HealthDataSubmission submission = submissionCaptor.getValue();
        assertEquals(submission.getAppVersion(), APP_VERSION);
        assertEquals(submission.getCreatedOn(), CREATED_ON);
        assertEquals(submission.getPhoneInfo(), PHONE_INFO);
        assertEquals(submission.getSchemaId(), SCHEMA_ID);
        assertEquals(submission.getSchemaRevision().intValue(), SCHEMA_REV);

        JsonNode data = submission.getData();
        assertEquals(data.size(), 2);
        assertEquals(data.get("foo").textValue(), "foo-value");
        assertEquals(data.get("bar").intValue(), 42);

        // validate metrics
        verify(mockMetrics).setRecordId(TEST_RECORD_ID);

        // validate request info uploadedOn - Time zone doesn't matter because we flatten everything to UTC anyway.
        ArgumentCaptor<RequestInfo> requestInfoCaptor = ArgumentCaptor.forClass(RequestInfo.class);
        verify(mockCacheProvider).updateRequestInfo(requestInfoCaptor.capture());

        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals(requestInfo.getUploadedOn().getMillis(), MOCK_NOW_MILLIS);
    }

    @Test
    public void submitHealthDataForParticipant() throws Exception {
        // mock request JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"" + CREATED_ON_STR + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"data\":{\n" +
                "       \"foo\":\"other value\",\n" +
                "       \"bar\":37\n" +
                "   }\n" +
                "}";
        mockRequestBody(mockRequest, jsonText);

        // mock back-end call - We only care about record ID. Also add Health Code to make sure it's being filtered.
        HealthDataRecord svcRecord = HealthDataRecord.create();
        svcRecord.setId(TEST_RECORD_ID);
        svcRecord.setHealthCode(HEALTH_CODE);
        when(mockHealthDataService.submitHealthData(any(), any(), any())).thenReturn(svcRecord);

        // execute and validate - Just check record ID. Health Code is filtered out.
        String result = controller.submitHealthDataForParticipant(USER_ID);
        HealthDataRecord controllerRecord = BridgeObjectMapper.get().readValue(result, HealthDataRecord.class);
        assertEquals(controllerRecord.getId(), TEST_RECORD_ID);
        assertNull(controllerRecord.getHealthCode());

        // validate call to healthDataService
        ArgumentCaptor<HealthDataSubmission> submissionCaptor = ArgumentCaptor.forClass(HealthDataSubmission.class);
        verify(mockHealthDataService).submitHealthData(eq(TestConstants.TEST_STUDY), same(OTHER_PARTICIPANT),
                submissionCaptor.capture());

        HealthDataSubmission submission = submissionCaptor.getValue();
        assertEquals(submission.getAppVersion(), APP_VERSION);
        assertEquals(submission.getCreatedOn(), CREATED_ON);
        assertEquals(submission.getPhoneInfo(), PHONE_INFO);
        assertEquals(submission.getSchemaId(), SCHEMA_ID);
        assertEquals(submission.getSchemaRevision().intValue(), SCHEMA_REV);

        JsonNode data = submission.getData();
        assertEquals(data.size(), 2);
        assertEquals(data.get("foo").textValue(), "other value");
        assertEquals(data.get("bar").intValue(), 37);

        // validate metrics
        verify(mockMetrics).setRecordId(TEST_RECORD_ID);
    }
    
    @Test
    public void updateRecordsStatus() throws Exception {
        // mock request JSON
        mockRequestBody(mockRequest, TEST_STATUS_JSON);

        when(mockHealthDataService.updateRecordsWithExporterStatus(any())).thenReturn(ImmutableList.of(TEST_RECORD_ID));

        // create a mock request entity
        RecordExportStatusRequest mockRequest = new RecordExportStatusRequest();
        mockRequest.setRecordIds(ImmutableList.of(TEST_RECORD_ID));
        mockRequest.setSynapseExporterStatus(TEST_STATUS);

        // execute and validate
        StatusMessage result = controller.updateRecordsStatus();
        assertEquals(result.getMessage(),
                "Update exporter status to: " + ImmutableList.of(TEST_RECORD_ID) + " complete.");

        // first verify if it calls the service
        verify(mockHealthDataService).updateRecordsWithExporterStatus(any());
        // then verify if it parse json correctly as a request entity
        verify(mockHealthDataService).updateRecordsWithExporterStatus(requestArgumentCaptor.capture());
        RecordExportStatusRequest capturedRequest = requestArgumentCaptor.getValue();
        assertEquals(TEST_RECORD_ID, capturedRequest.getRecordIds().get(0));
        assertEquals(TEST_STATUS, capturedRequest.getSynapseExporterStatus());
    }
}
