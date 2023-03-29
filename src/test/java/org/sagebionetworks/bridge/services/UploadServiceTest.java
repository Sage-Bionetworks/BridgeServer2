package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.upload.UploadCompletionClient.S3_WORKER;
import static org.sagebionetworks.bridge.models.upload.UploadStatus.SUCCEEDED;
import static org.sagebionetworks.bridge.models.upload.UploadStatus.VALIDATION_IN_PROGRESS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.models.upload.UploadViewEx3;
import org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator;

@SuppressWarnings("ConstantConditions")
public class UploadServiceTest {
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(TestConstants.UA);

    private static final DateTime START_TIME = DateTime.parse("2016-04-02T10:00:00.000Z");
    private static final DateTime END_TIME = DateTime.parse("2016-04-03T10:00:00.000Z");
    private static final String INSTANCE_GUID = "dummy-instance-guid";
    private static final String MOCK_OFFSET_KEY = "mock-offset-key";
    private static final String UPLOAD_BUCKET_NAME = "upload-bucket";
    final static DateTime TIMESTAMP = DateTime.now();
    final static String ORIGINAL_UPLOAD_ID = "anOriginalUploadId";
    final static String NEW_UPLOAD_ID = "aNewUploadId";
    private static final String UPLOAD_ID_1 = "upload1";
    private static final String UPLOAD_ID_2 = "upload2";
    final static String RECORD_ID = "aRecordId";
    final static StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build();

    @Mock
    private AccountService mockAccountService;

    @Mock
    private AdherenceService mockAdherenceService;

    @Mock
    private AppService mockAppService;

    @Mock
    HealthDataService mockHealthDataService;

    @Mock
    private HealthDataEx3Service mockHealthDataEx3Service;

    @Mock
    Upload mockUpload;

    @Mock
    HealthDataRecord mockRecord;
    
    @Mock
    Upload mockFailedUpload;

    @Mock
    AmazonS3 mockS3UploadClient;
    
    @Mock
    AmazonS3 mockS3Client;

    @Mock
    private Schedule2Service mockSchedule2Service;

    @Mock
    UploadValidationService mockUploadValidationService;

    @Mock
    UploadDedupeDao mockUploadDedupeDao;
    
    @Mock
    UploadDao mockUploadDao;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Captor
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor;
    
    @InjectMocks
    UploadService svc;
    
    @BeforeMethod
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        MockitoAnnotations.initMocks(this);
        svc.setS3Client(mockS3Client);
        svc.setS3UploadClient(mockS3UploadClient);
        
        when(mockConfig.getProperty(UploadService.CONFIG_KEY_UPLOAD_BUCKET)).thenReturn(UPLOAD_BUCKET_NAME);
        svc.setConfig(mockConfig);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @AfterClass
    public static void afterClass() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getNullUploadId() {
        svc.getUpload(null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getEmptyUploadId() {
        svc.getUpload("");
    }

    @Test
    public void getUpload() {
        // This is a simple call through to the DAO. Test the data flow.

        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getUpload");
        when(mockUploadDao.getUpload("test-upload-id")).thenReturn(mockUpload);

        // execute and validate
        Upload retVal = svc.getUpload("test-upload-id");
        assertSame(retVal, mockUpload);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStatusNullUploadId() {
        svc.getUploadValidationStatus(null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStatusEmptyUploadId() {
        svc.getUploadValidationStatus("");
    }

    @Test
    public void getStatus() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatus");
        mockUpload.setStatus(UploadStatus.VALIDATION_FAILED);
        mockUpload.setUploadId("no-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatus - message"));

        when(mockUploadDao.getUpload("no-record-id")).thenReturn(mockUpload);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("no-record-id");
        assertEquals(status.getId(), "no-record-id");
        assertEquals(status.getStatus(), UploadStatus.VALIDATION_FAILED);
        assertNull(status.getRecord());

        assertEquals(status.getMessageList().size(), 1);
        assertEquals(status.getMessageList().get(0), "getStatus - message");
    }

    @Test
    public void getStatusWithRecord() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatusWithRecord");
        mockUpload.setRecordId("test-record-id");
        mockUpload.setStatus(UploadStatus.SUCCEEDED);
        mockUpload.setUploadId("with-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatusWithRecord - message"));

        when(mockUploadDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        HealthDataRecord dummyRecord = HealthDataRecord.create();
        when(mockHealthDataService.getRecordById("test-record-id")).thenReturn(dummyRecord);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("with-record-id");
        assertEquals(status.getId(), "with-record-id");
        assertEquals(status.getStatus(), UploadStatus.SUCCEEDED);
        assertSame(status.getRecord(), dummyRecord);

        assertEquals(status.getMessageList().size(), 1);
        assertEquals(status.getMessageList().get(0), "getStatusWithRecord - message");
    }

    // branch coverage
    @Test
    public void getStatusRecordIdWithNoRecord() {
        // mock upload dao
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setHealthCode("getStatusRecordIdWithNoRecord");
        mockUpload.setRecordId("missing-record-id");
        mockUpload.setStatus(UploadStatus.SUCCEEDED);
        mockUpload.setUploadId("with-record-id");
        mockUpload.setValidationMessageList(ImmutableList.of("getStatusRecordIdWithNoRecord - message"));

        when(mockUploadDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        when(mockHealthDataService.getRecordById("missing-record-id")).thenThrow(IllegalArgumentException.class);

        // execute and validate
        UploadValidationStatus status = svc.getUploadValidationStatus("with-record-id");
        assertEquals(status.getId(), "with-record-id");
        assertEquals(status.getStatus(), UploadStatus.SUCCEEDED);
        assertNull(status.getRecord());

        assertEquals(status.getMessageList().size(), 1);
        assertEquals(status.getMessageList().get(0), "getStatusRecordIdWithNoRecord - message");
    }
    
    @Test
    public void getUploadView() {
        DynamoUpload2 mockUpload = new DynamoUpload2();
        mockUpload.setRecordId("test-record-id");
        mockUpload.setUploadId("with-record-id");
        when(mockUploadDao.getUpload("with-record-id")).thenReturn(mockUpload);

        // mock health data service
        HealthDataRecord dummyRecord = HealthDataRecord.create();
        dummyRecord.setSchemaId("schema-id");
        when(mockHealthDataService.getRecordById("test-record-id")).thenReturn(dummyRecord);

        // execute and validate
        UploadView uploadView = svc.getUploadView("with-record-id");
        assertEquals(uploadView.getUpload().getRecordId(), "test-record-id");
        assertEquals(uploadView.getUpload().getUploadId(), "with-record-id");
        assertEquals(uploadView.getHealthData().getSchemaId(), "schema-id");
    }
    
    private void setupUploadMocks() {
        // Mock upload
        doReturn(UploadStatus.SUCCEEDED).when(mockUpload).getStatus();
        doReturn("record-id").when(mockUpload).getRecordId();
        
        // Failed mock upload
        doReturn(UploadStatus.REQUESTED).when(mockFailedUpload).getStatus();

        // Mock upload with record ID but no record
        Upload mockUploadWithNoRecord = mock(Upload.class);
        when(mockUploadWithNoRecord.getStatus()).thenReturn(UploadStatus.SUCCEEDED);
        when(mockUploadWithNoRecord.getRecordId()).thenReturn("missing-record-id");

        // Mock getUploads/getUpload calls
        List<Upload> results = ImmutableList.of(mockUpload, mockFailedUpload, mockUploadWithNoRecord);
        
        ForwardCursorPagedResourceList<Upload> pagedListWithoutOffsetKey = new ForwardCursorPagedResourceList<>(results, null)
                .withRequestParam(ResourceList.PAGE_SIZE, API_MAXIMUM_PAGE_SIZE);
        doReturn(pagedListWithoutOffsetKey).when(mockUploadDao).getUploads(eq("ABC"), any(DateTime.class), any(DateTime.class), eq(0), eq(null));
        
        ForwardCursorPagedResourceList<Upload> pagedList = new ForwardCursorPagedResourceList<>(results, MOCK_OFFSET_KEY)
            .withRequestParam(ResourceList.PAGE_SIZE, API_MAXIMUM_PAGE_SIZE);
        doReturn(pagedList).when(mockUploadDao).getAppUploads(TEST_APP_ID, START_TIME, END_TIME, API_MAXIMUM_PAGE_SIZE, MOCK_OFFSET_KEY);
        doReturn(pagedList).when(mockUploadDao).getAppUploads(TEST_APP_ID, START_TIME, END_TIME, API_DEFAULT_PAGE_SIZE, null);
        
        // Mock the record returned from the validation status record
        doReturn("schema-id").when(mockRecord).getSchemaId();
        doReturn(10).when(mockRecord).getSchemaRevision();
        doReturn(HealthDataRecord.ExporterStatus.SUCCEEDED).when(mockRecord).getSynapseExporterStatus();
        // Mock UploadValidationStatus from health data record;
        doReturn(mockRecord).when(mockHealthDataService).getRecordById("record-id");
    }
    
    // Mock a successful and unsuccessful upload. The successful upload should call to get information 
    // from the health data record table (schema id/revision). All should be merged correctly in the 
    // resulting views.
    @Test
    public void canGetUploads() throws Exception {
        setupUploadMocks();
        ForwardCursorPagedResourceList<UploadView> returned = svc.getUploads("ABC", START_TIME, END_TIME, 0, null);
        
        verify(mockUploadDao).getUploads("ABC", START_TIME, END_TIME, 0, null);
        validateUploadMocks(returned, null);
    }
    
    @Test
    public void canGetStudyUploads() throws Exception {
        setupUploadMocks();
        
        // Now verify the app uploads works
        ForwardCursorPagedResourceList<UploadView> returned = svc.getAppUploads(TEST_APP_ID,
                START_TIME, END_TIME, API_MAXIMUM_PAGE_SIZE, MOCK_OFFSET_KEY);
        
        verify(mockUploadDao).getAppUploads(TEST_APP_ID, START_TIME, END_TIME, API_MAXIMUM_PAGE_SIZE, MOCK_OFFSET_KEY);
        validateUploadMocks(returned, MOCK_OFFSET_KEY);
    }

    @Test
    public void canGetStudyUploadsWithoutPageSize() throws Exception {
        setupUploadMocks();

        svc.getAppUploads(TEST_APP_ID, START_TIME, END_TIME, null, null);

        verify(mockUploadDao).getAppUploads(TEST_APP_ID, START_TIME, END_TIME, API_DEFAULT_PAGE_SIZE, null);
    }

    private void validateUploadMocks(ForwardCursorPagedResourceList<UploadView> returned, String expectedOffsetKey) {
        verify(mockHealthDataService).getRecordById("record-id");
        verify(mockHealthDataService).getRecordById("missing-record-id");
        verifyNoMoreInteractions(mockHealthDataService);

        List<? extends UploadView> uploadList = returned.getItems();
        assertEquals(uploadList.size(), 3);

        assertEquals(returned.getRequestParams().get("pageSize"), API_MAXIMUM_PAGE_SIZE);
        assertEquals(returned.getNextPageOffsetKey(), expectedOffsetKey);

        // The two sources of information are combined in the view.
        UploadView view = uploadList.get(0);
        assertEquals(view.getUpload().getStatus(), UploadStatus.SUCCEEDED);
        assertEquals(view.getUpload().getRecordId(), "record-id");
        assertEquals(view.getSchemaId(), "schema-id");
        assertEquals(view.getSchemaRevision(), new Integer(10));
        assertEquals(view.getHealthRecordExporterStatus(), HealthDataRecord.ExporterStatus.SUCCEEDED);
        assertNull(view.getHealthData());

        UploadView failedView = uploadList.get(1);
        assertEquals(failedView.getUpload().getStatus(), UploadStatus.REQUESTED);
        assertNull(failedView.getUpload().getRecordId());
        assertNull(failedView.getSchemaId());
        assertNull(failedView.getSchemaRevision());
        assertNull(failedView.getHealthRecordExporterStatus());

        UploadView viewWithNoRecord = uploadList.get(2);
        assertEquals(viewWithNoRecord.getUpload().getStatus(), UploadStatus.SUCCEEDED);
        assertEquals(viewWithNoRecord.getUpload().getRecordId(), "missing-record-id");
        assertNull(viewWithNoRecord.getSchemaId());
        assertNull(viewWithNoRecord.getSchemaRevision());
        assertNull(viewWithNoRecord.getHealthRecordExporterStatus());
    }

    @Test
    public void canPassStartTimeOnly() {
        setupUploadMocks();
        
        svc.getUploads("ABC", START_TIME, null, 0, null);
        verify(mockUploadDao).getUploads("ABC", START_TIME, END_TIME, 0, null);
    }
    
    @Test
    public void canPassEndTimeOnly() {
        setupUploadMocks();
        
        svc.getUploads("ABC", null, END_TIME, 0, null);
        verify(mockUploadDao).getUploads("ABC", START_TIME, END_TIME, 0, null);
    }

    @Test
    public void canPassNoTimes() {
        setupUploadMocks();
        
        ArgumentCaptor<DateTime> start = ArgumentCaptor.forClass(DateTime.class);
        ArgumentCaptor<DateTime> end = ArgumentCaptor.forClass(DateTime.class);
        
        svc.getUploads("ABC", null, null, 0, null);
        
        verify(mockUploadDao).getUploads(eq("ABC"), start.capture(), end.capture(), eq(0), eq(null));
        
        DateTime actualStart = start.getValue();
        DateTime actualEnd = end.getValue();
        assertEquals(actualEnd, actualStart.plusDays(1));
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void verifiesEndTimeNotBeforeStartTime() {
        svc.getUploads("ABC", END_TIME, START_TIME, 0, null);
    }
    
    @Test
    public void deleteUploadsByHealthCodeWorks() {
        // Mock DAO.
        when(mockUploadDao.deleteUploadsForHealthCode(HEALTH_CODE)).thenReturn(ImmutableList.of(UPLOAD_ID_1,
                UPLOAD_ID_2));

        // Execute.
        svc.deleteUploadsForHealthCode(HEALTH_CODE);

        // Verify dependencies.
        verify(mockUploadDao).deleteUploadsForHealthCode(HEALTH_CODE);
        verify(mockS3Client).deleteObject(UPLOAD_BUCKET_NAME, UPLOAD_ID_1);
        verify(mockS3Client).deleteObject(UPLOAD_BUCKET_NAME, UPLOAD_ID_2);
    }
    
    @Test
    public void deleteUploadsByHealthCodeRequiresHealthCode() {
        try {
            svc.deleteUploadsForHealthCode("");
            fail("Should have thrown exception");
        } catch(IllegalArgumentException e) {
            // expected
        }
        verify(mockUploadDao, never()).deleteUploadsForHealthCode(any());
    }
    
    @Test
    public void createUpload() throws Exception {
        // Set up RequestContext.
        RequestContext.set(new RequestContext.Builder().withUserAgent(TestConstants.UA).build());

        // Create input.
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(NEW_UPLOAD_ID);

        // Mock dependencies.
        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockUploadDao.createUpload(uploadRequest, TEST_APP_ID, HEALTH_CODE, null)).thenReturn(upload);
        when(mockS3UploadClient.generatePresignedUrl(any())).thenReturn(new URL("https://ws.com/some-link"));

        // Execute and verify.
        UploadSession session = svc.createUpload(TEST_APP_ID, PARTICIPANT, uploadRequest);
        assertEquals(session.getId(), NEW_UPLOAD_ID);
        assertEquals(session.getUrl(), "https://ws.com/some-link");
        assertEquals(session.getExpires(), TIMESTAMP.getMillis() + UploadService.EXPIRATION);

        verify(mockS3UploadClient).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET_NAME);
        assertEquals(request.getContentMd5(), "AAAAAAAAAAAAAAAAAAAAAA==");
        assertEquals(request.getContentType(), "application/binary");
        assertEquals(request.getExpiration(), new Date(TIMESTAMP.getMillis() + UploadService.EXPIRATION));
        assertEquals(request.getMethod(), HttpMethod.PUT);
        assertEquals(request.getRequestParameters().get(SERVER_SIDE_ENCRYPTION), AES_256_SERVER_SIDE_ENCRYPTION);

        // Verify client info and user agent.
        String clientInfoJsonText = upload.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, CLIENT_INFO);

        assertEquals(upload.getUserAgent(), TestConstants.UA);

        // Verify that we save the updated upload back to the DAO.
        verify(mockUploadDao).updateUpload(same(upload));
    }
    
    @Test
    public void createUploadNoDedupingAPIApp() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(NEW_UPLOAD_ID);
        
        when(mockUploadDao.createUpload(uploadRequest, API_APP_ID, HEALTH_CODE, null)).thenReturn(upload);
        when(mockS3UploadClient.generatePresignedUrl(any())).thenReturn(new URL("https://ws.com/some-link"));
        
        UploadSession session = svc.createUpload(API_APP_ID, PARTICIPANT, uploadRequest);
        assertEquals(session.getId(), NEW_UPLOAD_ID);
        assertEquals(session.getUrl(), "https://ws.com/some-link");
        assertEquals(session.getExpires(), TIMESTAMP.getMillis() + UploadService.EXPIRATION);
        
        verify(mockUploadDedupeDao, never()).getDuplicate(any(), any(), any());
        verify(mockUploadDao, never()).getUpload(any());
    }
    
    @Test
    public void createUploadDuplicate() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        
        when(mockUploadDedupeDao.getDuplicate(eq(HEALTH_CODE), eq("AAAAAAAAAAAAAAAAAAAAAA=="), any()))
                .thenReturn(ORIGINAL_UPLOAD_ID);
        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockUploadDao.createUpload(uploadRequest, TEST_APP_ID, HEALTH_CODE, ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockS3UploadClient.generatePresignedUrl(any())).thenReturn(new URL("https://ws.com/some-link"));
        
        UploadSession session = svc.createUpload(TEST_APP_ID, PARTICIPANT, uploadRequest);
        assertEquals(session.getId(), ORIGINAL_UPLOAD_ID);
        assertEquals(session.getUrl(), "https://ws.com/some-link");
        assertEquals(session.getExpires(), TIMESTAMP.getMillis() + UploadService.EXPIRATION);
        
        verify(mockS3UploadClient).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET_NAME);
        assertEquals(request.getContentMd5(), "AAAAAAAAAAAAAAAAAAAAAA==");
        assertEquals(request.getContentType(), "application/binary");
        assertEquals(request.getExpiration(), new Date(TIMESTAMP.getMillis() + UploadService.EXPIRATION));
        assertEquals(request.getMethod(), HttpMethod.PUT);
        assertEquals(request.getRequestParameters().get(SERVER_SIDE_ENCRYPTION), AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test
    public void getUploads() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        
        DateTime startTime = TIMESTAMP.minusDays(7);
        DateTime endTime = TIMESTAMP;
        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(ImmutableList.of(upload),
                null);
        when(mockUploadDao.getUploads(HEALTH_CODE, startTime, endTime, 40, "offsetKey")).thenReturn(uploads);
            
        ForwardCursorPagedResourceList<UploadView> result = svc.getUploads(HEALTH_CODE, startTime, endTime, 40,
                "offsetKey");
        assertEquals(result.getItems().get(0).getUpload(), upload);
    }
    
    @Test
    public void getUploadsWithDefaultValues() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        
        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(ImmutableList.of(upload),
                null);
        when(mockUploadDao.getUploads(HEALTH_CODE, TIMESTAMP.minusDays(1), TIMESTAMP, API_DEFAULT_PAGE_SIZE, null)).thenReturn(uploads);
        
        ForwardCursorPagedResourceList<UploadView> result = svc.getUploads(HEALTH_CODE, null, null, null, null);
        assertEquals(result.getItems().size(), 1);
    }
    
    @Test
    public void pollUploadValidationStatusWhenComplete() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        upload.setRecordId(RECORD_ID);
        upload.setStatus(SUCCEEDED);
        upload.setValidationMessageList(ImmutableList.of("One validation error"));

        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockHealthDataService.getRecordById(RECORD_ID)).thenReturn(mockRecord);
        
        UploadValidationStatus result = svc.pollUploadValidationStatusUntilComplete(ORIGINAL_UPLOAD_ID);
        assertEquals(result.getId(), upload.getUploadId());
        assertEquals(result.getRecord(), mockRecord);
        assertEquals(result.getStatus(), SUCCEEDED);
        assertEquals(result.getMessageList().size(), 1);
        assertEquals(result.getMessageList().get(0), "One validation error");
    }
    
    @Test(expectedExceptions = BridgeServiceException.class, 
            expectedExceptionsMessageRegExp = "Timeout polling validation status for upload anOriginalUploadId")
    public void pollUploadValidationStatusInProgress() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        upload.setRecordId(RECORD_ID);
        upload.setStatus(VALIDATION_IN_PROGRESS);
        upload.setValidationMessageList(ImmutableList.of("One validation error"));

        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockHealthDataService.getRecordById(RECORD_ID)).thenReturn(mockRecord);
        
        svc.setPollValidationStatusSleepMillis(10); // speed this up
        svc.pollUploadValidationStatusUntilComplete(ORIGINAL_UPLOAD_ID);
    }
    
    @Test
    public void uploadComplete() throws Exception {
        App app = App.create();
        app.setExporter3Enabled(false);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        upload.setRecordId(RECORD_ID);
        
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(UPLOAD_BUCKET_NAME, ORIGINAL_UPLOAD_ID)).thenReturn(metadata);
        
        svc.uploadComplete(TEST_APP_ID, S3_WORKER, upload, true);
        
        verify(mockUploadDao).uploadComplete(S3_WORKER, upload);
        verify(mockUploadValidationService).validateUpload(TEST_APP_ID, upload);
    }
    
    @Test
    public void uploadCompleteCannotBeValidated() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setStatus(UploadStatus.SUCCEEDED);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        upload.setRecordId(RECORD_ID);

        svc.uploadComplete(TEST_APP_ID, S3_WORKER, upload, false);
        
        verify(mockS3Client, never()).getObjectMetadata(any(), any());
        verify(mockUploadDao, never()).uploadComplete(any(), any());
        verify(mockUploadValidationService, never()).validateUpload(any(), any());
    }

    @Test(expectedExceptions = BridgeServiceException.class)
    public void uploadCompleteObjectMetadataException() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        
        AmazonS3Exception ex = new AmazonS3Exception("Error message");
        when(mockS3Client.getObjectMetadata(UPLOAD_BUCKET_NAME, ORIGINAL_UPLOAD_ID)).thenThrow(ex);
        
        svc.uploadComplete(TEST_APP_ID, S3_WORKER, upload, true);
    }
    
    @Test(expectedExceptions = NotFoundException.class)
    public void uploadCompleteObjectMetadataNotFound() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        
        AmazonS3Exception ex = new AmazonS3Exception("Error message");
        ex.setStatusCode(404);
        when(mockS3Client.getObjectMetadata(UPLOAD_BUCKET_NAME, ORIGINAL_UPLOAD_ID)).thenThrow(ex);
        
        svc.uploadComplete(TEST_APP_ID, S3_WORKER, upload, true);
    }
    
    @Test
    public void uploadCompleteConcurrentModificationException() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        upload.setRecordId(RECORD_ID);
        
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(UPLOAD_BUCKET_NAME, ORIGINAL_UPLOAD_ID)).thenReturn(metadata);
        
        doThrow(new ConcurrentModificationException("error")).when(mockUploadDao).uploadComplete(S3_WORKER, upload);
        
        svc.uploadComplete(TEST_APP_ID, S3_WORKER, upload, true);
        
        verify(mockUploadDao).uploadComplete(S3_WORKER, upload);
        verify(mockUploadValidationService, never()).validateUpload(TEST_APP_ID, upload);
    }
    
    UploadRequest constructUploadRequest() {
        return new UploadRequest.Builder().withName("oneUpload").withContentLength(1048L)
                .withContentMd5("AAAAAAAAAAAAAAAAAAAAAA==")
                .withContentType("application/binary").build();
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Adherence requires study ID")
    public void getUploadViewForExporter3_FetchAdherenceWithNoStudyId() {
        svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1, false, true);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadViewForExporter3_UploadFromWrongApp() {
        Upload upload = makeUploadForEx3Test();
        upload.setAppId("wrong-app");
        setupGetUploadViewForExporter3Test(upload, makeRecordForEx3Test());

        svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1, false, false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadViewForExporter3_RecordFromWrongApp() {
        HealthDataRecordEx3 healthDataRecord = makeRecordForEx3Test();
        healthDataRecord.setAppId("wrong-app");
        setupGetUploadViewForExporter3Test(makeUploadForEx3Test(), healthDataRecord);

        svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1, false, false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadViewForExporter3_NoUploadNoRecord() {
        setupGetUploadViewForExporter3Test(null, null);
        svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1, false, false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadViewForExporter3_AccountNotFound() {
        setupGetUploadViewForExporter3Test(makeUploadForEx3Test(), makeRecordForEx3Test());

        // Override mock to return no account ID.
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE)).thenReturn(
                Optional.empty());

        svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1, false, false);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getUploadViewForExporter3_NotPermitted() {
        setupGetUploadViewForExporter3Test(makeUploadForEx3Test(), makeRecordForEx3Test());

        // Just test one case where we don't have permissions. The full permissions test is tested in AuthUtilsTest.
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("other-id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());

        svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1, false, false);
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_NoTimelineNoAdherence() {
        // Set up test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1,
                false, false);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertNull(uploadView.getAdherenceRecords());
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Verify back-end calls.
        verify(mockUploadDao).getUploadNoThrow(UPLOAD_ID_1);
        verify(mockHealthDataEx3Service).getRecord(UPLOAD_ID_1, false);
        verify(mockAccountService).getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE);
        verifyZeroInteractions(mockSchedule2Service, mockAdherenceService);
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_WithTimeline() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        mockTimelineMetadataForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1,
                true, false);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertNull(uploadView.getAdherenceRecords());
        assertSame(uploadView.getRecord(), record);
        assertEquals(uploadView.getTimelineMetadata().getMetadata().get("assessmentInstanceGuid"), INSTANCE_GUID);
        assertSame(uploadView.getUpload(), upload);

        // Verify timeline call, but no adherence call.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verifyZeroInteractions(mockAdherenceService);
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_WithAdherence() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                false, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Verify adherence call, but no timeline call.
        verifyZeroInteractions(mockSchedule2Service);

        ArgumentCaptor<AdherenceRecordsSearch> searchCaptor = ArgumentCaptor.forClass(AdherenceRecordsSearch.class);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), searchCaptor.capture());
        AdherenceRecordsSearch search = searchCaptor.getValue();
        assertEquals(search.getInstanceGuids(), ImmutableSet.of(INSTANCE_GUID));
        assertEquals(search.getPageSize().intValue(), AdherenceRecordsSearchValidator.MAX_PAGE_SIZE);
        assertEquals(search.getStudyId(), TEST_STUDY_ID);
        assertEquals(search.getUserId(), TEST_USER_ID);
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_WithBothTimelineAndAdherence() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertEquals(uploadView.getTimelineMetadata().getMetadata().get("assessmentInstanceGuid"), INSTANCE_GUID);
        assertSame(uploadView.getUpload(), upload);

        // Verify both timeline and adherence calls. (Don't worry about search parameters.)
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_UploadWithoutRecord() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        setupGetUploadViewForExporter3Test(upload, null);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertNotNull(uploadView.getTimelineMetadata());
        assertNull(uploadView.getRecord());
        assertSame(uploadView.getUpload(), upload);

        // Verify both timeline and adherence calls. (Don't worry about search parameters.)
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_RecordWithoutUpload() {
        // Setup test.
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(null, record);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertNotNull(uploadView.getTimelineMetadata());
        assertNull(uploadView.getUpload());

        // Verify both timeline and adherence calls. (Don't worry about search parameters.)
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_UploadWithoutMetadata() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        upload.setMetadata(null);

        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertNotNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // We can still call timeline and adherence because we fallback to the record metadata.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_UploadWithoutInstanceGuid() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        upload.getMetadata().remove(UploadService.METADATA_KEY_INSTANCE_GUID);

        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertNotNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // We can still call timeline and adherence because we fallback to the record metadata.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_UploadInstanceGuidJsonNull() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        upload.getMetadata().set(UploadService.METADATA_KEY_INSTANCE_GUID, NullNode.getInstance());

        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertNotNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // We can still call timeline and adherence because we fallback to the record metadata.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_UploadInstanceGuidWrongType() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        upload.getMetadata().put(UploadService.METADATA_KEY_INSTANCE_GUID, 123);

        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        mockTimelineMetadataForExporter3Test();

        List<AdherenceRecord> adherenceRecordList = mockAdherenceForExporter3Test();

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertSame(uploadView.getAdherenceRecords(), adherenceRecordList);
        assertSame(uploadView.getRecord(), record);
        assertNotNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // We can still call timeline and adherence because we fallback to the record metadata.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_RecordWithoutMetadata() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        upload.setMetadata(null);

        HealthDataRecordEx3 record = makeRecordForEx3Test();
        record.setMetadata(null);

        setupGetUploadViewForExporter3Test(upload, record);

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertNull(uploadView.getAdherenceRecords());
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Without metadata, we can't call timeline or adherence.
        verifyZeroInteractions(mockSchedule2Service, mockAdherenceService);
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_RecordWithoutInstanceGuid() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        upload.setMetadata(null);

        HealthDataRecordEx3 record = makeRecordForEx3Test();
        record.getMetadata().remove(UploadService.METADATA_KEY_INSTANCE_GUID);

        setupGetUploadViewForExporter3Test(upload, record);

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                true, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertNull(uploadView.getAdherenceRecords());
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Without metadata, we can't call timeline or adherence.
        verifyZeroInteractions(mockSchedule2Service, mockAdherenceService);
    }

    @Test
    public void getUploadViewForExporter3_TimelineNotPresent() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        when(mockSchedule2Service.getTimelineMetadata(INSTANCE_GUID)).thenReturn(Optional.empty());

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1,
                true, false);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertNull(uploadView.getAdherenceRecords());
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Verify timeline call, but no adherence call.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verifyZeroInteractions(mockAdherenceService);
    }

    @Test
    public void getUploadViewForExporter3_TimelineFromWrongApp() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setAppId("wrong-app");
        timelineMetadata.setAssessmentInstanceGuid(INSTANCE_GUID);
        when(mockSchedule2Service.getTimelineMetadata(INSTANCE_GUID)).thenReturn(Optional.of(timelineMetadata));

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, null, UPLOAD_ID_1,
                true, false);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertNull(uploadView.getAdherenceRecords());
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Verify timeline call, but no adherence call.
        verify(mockSchedule2Service).getTimelineMetadata(INSTANCE_GUID);
        verifyZeroInteractions(mockAdherenceService);
    }

    @Test
    public void getUploadViewForExporter3_NormalCase_EmptyAdherenceRecords() {
        // Setup test.
        Upload upload = makeUploadForEx3Test();
        HealthDataRecordEx3 record = makeRecordForEx3Test();
        setupGetUploadViewForExporter3Test(upload, record);

        PagedResourceList<AdherenceRecord> pagedResourceList = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockAdherenceService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(pagedResourceList);

        // Execute and validate.
        UploadViewEx3 uploadView = svc.getUploadViewForExporter3(TEST_APP_ID, TEST_STUDY_ID, UPLOAD_ID_1,
                false, true);
        assertEquals(uploadView.getId(), UPLOAD_ID_1);
        assertEquals(uploadView.getHealthCode(), HEALTH_CODE);
        assertEquals(uploadView.getUserId(), TEST_USER_ID);
        assertTrue(uploadView.getAdherenceRecords().isEmpty());
        assertSame(uploadView.getRecord(), record);
        assertNull(uploadView.getTimelineMetadata());
        assertSame(uploadView.getUpload(), upload);

        // Verify adherence call, but no timeline call.
        verifyZeroInteractions(mockSchedule2Service);
        verify(mockAdherenceService).getAdherenceRecords(eq(TEST_APP_ID), any());
    }

    private void setupGetUploadViewForExporter3Test(Upload upload, HealthDataRecordEx3 record) {
        // Set request context. Developer has access to everything.
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("other-id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());

        // Mock upload and record.
        when(mockUploadDao.getUploadNoThrow(UPLOAD_ID_1)).thenReturn(upload);
        when(mockHealthDataEx3Service.getRecord(UPLOAD_ID_1, false)).thenReturn(Optional.ofNullable(record));

        // Mock account service.
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE)).thenReturn(
                Optional.of(TEST_USER_ID));
    }

    private static Upload makeUploadForEx3Test() {
        Upload upload = Upload.create();
        upload.setAppId(TEST_APP_ID);
        upload.setUploadId(UPLOAD_ID_1);
        upload.setHealthCode(HEALTH_CODE);

        ObjectNode metadataNode = BridgeObjectMapper.get().createObjectNode();
        metadataNode.put(UploadService.METADATA_KEY_INSTANCE_GUID, INSTANCE_GUID);
        upload.setMetadata(metadataNode);

        return upload;
    }

    private static HealthDataRecordEx3 makeRecordForEx3Test() {
        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setAppId(TEST_APP_ID);
        record.setHealthCode(HEALTH_CODE);
        record.setId(UPLOAD_ID_1);

        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put(UploadService.METADATA_KEY_INSTANCE_GUID, INSTANCE_GUID);
        record.setMetadata(metadataMap);

        return record;
    }

    private void mockTimelineMetadataForExporter3Test() {
        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setAppId(TEST_APP_ID);
        timelineMetadata.setAssessmentInstanceGuid(INSTANCE_GUID);

        when(mockSchedule2Service.getTimelineMetadata(INSTANCE_GUID)).thenReturn(Optional.of(timelineMetadata));
    }

    private List<AdherenceRecord> mockAdherenceForExporter3Test() {
        // We don't actually need any of the data in the adherence record, just the fact that it exists.
        List<AdherenceRecord> adherenceRecordList = ImmutableList.of(new AdherenceRecord());
        PagedResourceList<AdherenceRecord> pagedResourceList = new PagedResourceList<>(adherenceRecordList, 1);
        when(mockAdherenceService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(pagedResourceList);
        return adherenceRecordList;
    }
}
