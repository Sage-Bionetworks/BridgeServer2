package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.UploadService;

public class UploadControllerTest extends Mockito {
    private static final String RECORD_ID = "record-id";
    private static final String UPLOAD_ID = "upload-id";
    private static final String VALIDATION_ERROR_MESSAGE = "There was a validation error";

    @Spy
    @InjectMocks
    UploadController controller;
    
    @Mock
    UploadService mockUploadService;
    
    @Mock
    HealthDataService mockHealthDataService;
    
    @Mock
    HealthCodeDao mockHealthCodeDao;
    
    @Mock
    AccountDao mockAccountDao;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    UserSession mockWorkerSession;
    
    @Mock
    UserSession mockConsentedUserSession;
    
    @Mock
    UserSession mockOtherUserSession;
    
    @Mock
    UserSession mockResearcherSession;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock 
    Metrics mockMetrics;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<Upload> uploadCaptor;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    DynamoUpload2 upload;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // mock uploadService.getUpload()
        upload = new DynamoUpload2();
        upload.setHealthCode("consented-user-health-code");
        upload.setUploadId("upload-id");
        doReturn(upload).when(mockUploadService).getUpload("upload-id");
        
        upload.setStudyId("worker-health-code");

        HealthDataRecord record = HealthDataRecord.create();
        record.setId(RECORD_ID);
        record.setHealthCode(HEALTH_CODE);

        UploadValidationStatus status = new UploadValidationStatus.Builder()
                .withId(UPLOAD_ID)
                .withRecord(record)
                .withMessageList(Lists.newArrayList(VALIDATION_ERROR_MESSAGE))
                .withStatus(UploadStatus.VALIDATION_FAILED).build();

        doReturn(status).when(mockUploadService).getUploadValidationStatus(UPLOAD_ID);
        doReturn(status).when(mockUploadService).pollUploadValidationStatusUntilComplete(UPLOAD_ID);

        // mock metrics
        doReturn(mockMetrics).when(controller).getMetrics();

        // mock sessions
        doReturn(true).when(mockWorkerSession).isInRole(Roles.WORKER);
        
        doReturn("consented-user-health-code").when(mockConsentedUserSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("consented-user-study-id")).when(mockConsentedUserSession).getStudyIdentifier();
        doReturn("userId").when(mockConsentedUserSession).getId();
        doReturn(new StudyParticipant.Builder().build()).when(mockConsentedUserSession).getParticipant();
        
        doReturn("other-user-health-code").when(mockOtherUserSession).getHealthCode();
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void startingUploadRecordedInRequestInfo() throws Exception {
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        mockRequestBody(mockRequest, createJson(
            "{'name':'uploadName','contentLength':100,'contentMd5':'abc','contentType':'application/zip'}"));
        when(mockRequest.getHeader("User-Agent")).thenReturn("app/10");
        
        UploadSession uploadSession = new UploadSession("id", new URL("http://server.com/"), 1000);
        
        doReturn(uploadSession).when(mockUploadService).createUpload(any(), any(), any());
        
        controller.upload();
        
        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo info = requestInfoCaptor.getValue();
        assertNotNull(info.getUploadedOn());
        assertEquals(info.getUserId(), "userId");
    }
    
    @Test
    public void uploadCompleteAcceptsWorker() throws Exception {
        upload.setStudyId("consented-user-study-id");
        // setup controller
        doReturn(mockWorkerSession).when(controller).getAuthenticatedSession();

        // execute and validate
        String result = controller.uploadComplete(UPLOAD_ID, false, false);
        validateValidationStatus(result);

        // verify back-end calls
        verify(mockUploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.S3_WORKER), uploadCaptor.capture(), eq(false));
        Upload upload = uploadCaptor.getValue();
        assertEquals(upload.getHealthCode(), "consented-user-health-code");

        verify(mockUploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(mockUploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }
    
    @Test
    public void uploadCompleteWithMissingStudyId() throws Exception {
        upload.setStudyId(null); // no studyId, must look up by healthCode
        upload.setHealthCode(HEALTH_CODE);
        // setup controller
        doReturn(mockWorkerSession).when(controller).getAuthenticatedSession();
        doReturn("studyId").when(mockHealthCodeDao).getStudyIdentifier(HEALTH_CODE);

        // execute and validate
        String result = controller.uploadComplete(UPLOAD_ID, false, false);
        validateValidationStatus(result);

        // verify back-end calls
        verify(mockHealthCodeDao).getStudyIdentifier(HEALTH_CODE);
        verify(mockUploadService).uploadComplete(eq(new StudyIdentifierImpl("studyId")),
                eq(UploadCompletionClient.S3_WORKER), uploadCaptor.capture(), eq(false));
        Upload upload = uploadCaptor.getValue();
        assertEquals(upload.getHealthCode(), HEALTH_CODE);

        verify(mockUploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(mockUploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void uploadCompleteAcceptsConsentedUser() throws Exception {
        // setup controller
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedAndConsentedSession();

        // execute and validate
        String result = controller.uploadComplete(UPLOAD_ID, false, false);
        validateValidationStatus(result);

        // verify back-end calls
        verify(mockUploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), uploadCaptor.capture(), eq(false));
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());

        verify(mockUploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(mockUploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }
    
    @Test
    public void differentUserInSameStudyCannotCompleteUpload() throws Exception {
        // setup controller
        doReturn("other-health-code").when(mockOtherUserSession).getHealthCode();
        doReturn(false).when(mockOtherUserSession).isInRole(Roles.WORKER);
        
        doReturn(mockOtherUserSession).when(controller).getAuthenticatedSession();
        doReturn(mockOtherUserSession).when(controller).getAuthenticatedAndConsentedSession();

        // execute and catch exception
        try {
            controller.uploadComplete(UPLOAD_ID, false, false);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            // expected exception
        }

        // verify back-end calls
        verify(mockUploadService, never()).uploadComplete(any(), any(), any(), anyBoolean());
        verify(mockUploadService, never()).getUploadValidationStatus(any());
        verify(mockUploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void uploadCompleteSynchronousMode() throws Exception {
        // setup controller
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedAndConsentedSession();

        // execute and validate
        String result = controller.uploadComplete(UPLOAD_ID, true, false);
        validateValidationStatus(result);

        // verify back-end calls
        verify(mockUploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), any(), eq(false));
        verify(mockUploadService).pollUploadValidationStatusUntilComplete(UPLOAD_ID);
        verify(mockUploadService, never()).getUploadValidationStatus(any());
    }

    @Test
    public void uploadCompleteRedriveFlag() throws Exception {
        // setup controller
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedAndConsentedSession();

        // execute and validate
        String result = controller.uploadComplete(UPLOAD_ID, false, true);
        validateValidationStatus(result);

        // verify back-end calls
        verify(mockUploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), any(), eq(true));
        verify(mockUploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(mockUploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void getValidationStatusWorks() throws Exception {
        doReturn(mockConsentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        String result = controller.getValidationStatus(UPLOAD_ID);
        validateValidationStatus(result);
        verify(mockUploadService).getUploadValidationStatus(UPLOAD_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getValidationStatusEnforcesHealthCodeMatch() throws Exception {
        doReturn(mockOtherUserSession).when(controller).getAuthenticatedAndConsentedSession();
        controller.getValidationStatus(UPLOAD_ID);
    }
    
    @Test
    public void getUploadById() throws Exception {
        doReturn(mockResearcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        
        HealthDataRecord record = HealthDataRecord.create();
        record.setHealthCode(HEALTH_CODE);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("researcher-study-id");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).withHealthDataRecord(record).build();
        
        when(mockUploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);
        
        UploadView result = controller.getUpload(UPLOAD_ID);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(result);
        assertEquals(node.get("completedBy").textValue(), "s3_worker");
        assertEquals(node.get("type").textValue(), "Upload");
        assertEquals(node.get("healthData").get("healthCode").textValue(), HEALTH_CODE);
    }

    @Test
    public void getUploadByRecordId() throws Exception {
        doReturn(USER_ID).when(mockResearcherSession).getId();
        doReturn(TEST_STUDY).when(mockResearcherSession).getStudyIdentifier();
        doReturn(mockResearcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        
        HealthDataRecord record = HealthDataRecord.create();
        record.setStudyId(TEST_STUDY_IDENTIFIER);
        record.setUploadId(UPLOAD_ID);
        record.setHealthCode(HEALTH_CODE);
        when(mockHealthDataService.getRecordById("record-id")).thenReturn(record);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId(TEST_STUDY_IDENTIFIER);
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).withHealthDataRecord(record).build();
        
        when(mockUploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);

        UploadView result = controller.getUpload("recordId:record-id");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(result);
        assertEquals(node.get("completedBy").textValue(), "s3_worker");
        assertEquals(node.get("type").textValue(), "Upload");
        assertEquals(node.get("healthData").get("healthCode").textValue(), HEALTH_CODE);
    }

    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp=".*Study admin cannot retrieve upload in another study.*")
    public void getUploadByRecordIdRejectsStudyAdmin() throws Exception {
        doReturn(mockResearcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        doReturn(USER_ID).when(mockResearcherSession).getId();
        when(mockResearcherSession.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("researcher-study-id"));

        HealthDataRecord record = HealthDataRecord.create();
        record.setStudyId(TEST_STUDY_IDENTIFIER);
        record.setUploadId(UPLOAD_ID);
        record.setHealthCode(HEALTH_CODE);
        when(mockHealthDataService.getRecordById("record-id")).thenReturn(record);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("researcher-study-id");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).withHealthDataRecord(record).build();

        when(mockUploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);

        controller.getUpload("recordId:record-id");
    }

    @Test
    public void getUploadByRecordIdWorksForFullAdmin() throws Exception {
        doReturn(mockResearcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        doReturn(USER_ID).when(mockResearcherSession).getId();
        when(mockResearcherSession.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        when(mockAccountDao.getAccount(ACCOUNT_ID)).thenReturn(Account.create());

        HealthDataRecord record = HealthDataRecord.create();
        record.setStudyId("some-other-study");
        record.setUploadId(UPLOAD_ID);
        record.setHealthCode(HEALTH_CODE);
        when(mockHealthDataService.getRecordById("record-id")).thenReturn(record);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("some-other-study");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).withHealthDataRecord(record).build();

        when(mockUploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);

        UploadView view = controller.getUpload("recordId:record-id");
        assertNotNull(view);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadByRecordIdRecordMissing() throws Exception {
        doReturn(mockResearcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        
        when(mockHealthDataService.getRecordById("record-id")).thenReturn(null);

        controller.getUpload("recordId:record-id");
    }
    
    private static void validateValidationStatus(String result) throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(result);
        assertEquals(node.get("id").textValue(), UPLOAD_ID);
        assertEquals(node.get("status").textValue(), UploadStatus.VALIDATION_FAILED.toString().toLowerCase());
        assertEquals(node.get("type").textValue(), "UploadValidationStatus");

        JsonNode errors = node.get("messageList");
        assertEquals(errors.get(0).textValue(), VALIDATION_ERROR_MESSAGE);

        JsonNode recordNode = node.get("record");
        assertEquals(recordNode.get("id").textValue(), RECORD_ID);

        // Health code is filtered out of the record.
        assertNull(recordNode.get("healthCode"));
    }
}
