package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.upload.UploadCompletionClient.S3_WORKER;
import static org.sagebionetworks.bridge.models.upload.UploadStatus.SUCCEEDED;
import static org.sagebionetworks.bridge.models.upload.UploadStatus.VALIDATION_IN_PROGRESS;
import static org.sagebionetworks.bridge.services.UploadService.METADATA_KEY_EVENT_TIMESTAMP;
import static org.sagebionetworks.bridge.services.UploadService.METADATA_KEY_INSTANCE_GUID;
import static org.sagebionetworks.bridge.services.UploadService.METADATA_KEY_STARTED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
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
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;

@SuppressWarnings("ConstantConditions")
public class UploadServiceTest {
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(TestConstants.UA);

    private static final DateTime START_TIME = DateTime.parse("2016-04-02T10:00:00.000Z");
    private static final DateTime END_TIME = DateTime.parse("2016-04-03T10:00:00.000Z");
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
    Schedule2Service mockScheduleService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    UploadValidationService mockUploadValidationService;

    @Mock
    UploadDedupeDao mockUploadDedupeDao;
    
    @Mock
    UploadDao mockUploadDao;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Captor
    ArgumentCaptor<AdherenceRecordList> adherenceRecordListCaptor;
    
    @Captor
    ArgumentCaptor<AdherenceRecordsSearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor;
    
    @Captor
    ArgumentCaptor<List<AdherenceRecord>> recordListCaptor;
    
    @InjectMocks
    @Spy
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
        RequestContext.set(new RequestContext.Builder().withCallerClientInfo(CLIENT_INFO).build());

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

        String clientInfoJsonText = upload.getClientInfo();
        ClientInfo deser = BridgeObjectMapper.get().readValue(clientInfoJsonText, ClientInfo.class);
        assertEquals(deser, CLIENT_INFO);
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
        
        doNothing().when(svc).updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        svc.uploadComplete(TEST_APP_ID, S3_WORKER, upload, true);
        
        verify(mockUploadDao).uploadComplete(S3_WORKER, upload);
        verify(mockUploadValidationService).validateUpload(TEST_APP_ID, upload);
        verify(svc).updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
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
        verify(svc, never()).updateAdherenceWithUploadInfo(any(), any());
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
        verify(svc, never()).updateAdherenceWithUploadInfo(any(), any());
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_nullMetadata() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAccountService, mockAdherenceService, mockScheduleService, mockStudyService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_noInstanceGuidMetadata() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setMetadata(constructMetadata("other-key", GUID, 
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(), 
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAccountService, mockAdherenceService, mockScheduleService, mockStudyService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_noEventTimestampMetadata() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                "other-key", TIMESTAMP.toString(), 
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAccountService, mockAdherenceService, mockScheduleService, mockStudyService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_noStartedOnMetadata() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID, 
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(), 
                "other-key", TIMESTAMP.plusHours(1).toString()));
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAccountService, mockAdherenceService, mockScheduleService, mockStudyService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_malformedEventTimestamp() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, "not-a-timestamp",
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAccountService, mockAdherenceService, mockScheduleService, mockStudyService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_malformedStartedOn() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, "not-a-timestamp"));
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAccountService, mockAdherenceService, mockScheduleService, mockStudyService);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAdherenceWithUploadInfo_participantAccountNotFound() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE))
                .thenReturn(Optional.empty());
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_noTimelineMetadata() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE))
                .thenReturn(Optional.of(TEST_USER_ID));
        
        when(mockScheduleService.getTimelineMetadata(GUID)).thenReturn(Optional.empty());
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
    
        verifyNoMoreInteractions(mockAdherenceService, mockStudyService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_noStudiesRelatedToSchedule() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE))
                .thenReturn(Optional.of(TEST_USER_ID));
    
        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setScheduleGuid(SCHEDULE_GUID);
        
        when(mockScheduleService.getTimelineMetadata(GUID)).thenReturn(Optional.of(timelineMetadata));
        
        when(mockStudyService.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID))
                .thenReturn(ImmutableList.of());
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAdherenceService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_multipleStudiesRelatedToSchedule() throws JsonProcessingException {
        // This is an extreme edge case, but it's technically possible to have multiple studies sharing the same
        // schedule.
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE))
                .thenReturn(Optional.of(TEST_USER_ID));
        
        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setScheduleGuid(SCHEDULE_GUID);
        
        when(mockScheduleService.getTimelineMetadata(GUID)).thenReturn(Optional.of(timelineMetadata));
        
        when(mockStudyService.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID))
                .thenReturn(ImmutableList.of(TEST_STUDY_ID, "other-study"));
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        verifyNoMoreInteractions(mockAdherenceService);
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_successfullyCreateNewRecord() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setCompletedOn(TIMESTAMP.plusHours(1).getMillis());
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE))
                .thenReturn(Optional.of(TEST_USER_ID));
        
        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setScheduleGuid(SCHEDULE_GUID);
        
        when(mockScheduleService.getTimelineMetadata(GUID)).thenReturn(Optional.of(timelineMetadata));
        
        when(mockStudyService.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID))
                .thenReturn(ImmutableList.of(TEST_STUDY_ID));
        
        PagedResourceList<AdherenceRecord> searchResult = new PagedResourceList<>(ImmutableList.of(), 0);
        
        when(mockAdherenceService.getAdherenceRecords(eq(TEST_APP_ID), searchCaptor.capture())).thenReturn(searchResult);
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        // Verify record search includes required fields.
        AdherenceRecordsSearch capturedSearch = searchCaptor.getValue();
        assertEquals(capturedSearch.getUserId(), TEST_USER_ID);
        assertEquals(capturedSearch.getStudyId(), TEST_STUDY_ID);
        assertEquals(capturedSearch.getInstanceGuids().size(), 1);
        assertTrue(capturedSearch.getInstanceGuids().contains(GUID));
        
        // Verify the new adherence record is sent for update.
        verify(mockAdherenceService).updateAdherenceRecords(eq(TEST_APP_ID), adherenceRecordListCaptor.capture());
        
        assertNotNull(adherenceRecordListCaptor);
        assertNotNull(adherenceRecordListCaptor.getValue());
        AdherenceRecordList capturedAdherenceRecordList = adherenceRecordListCaptor.getValue();
        assertNotNull(capturedAdherenceRecordList.getRecords());
        List<AdherenceRecord> capturedRecordList = capturedAdherenceRecordList.getRecords();
        assertEquals(capturedRecordList.size(), 1);
        AdherenceRecord capturedRecord = capturedRecordList.get(0);
        assertEquals(capturedRecord.getAppId(), TEST_APP_ID);
        assertEquals(capturedRecord.getStudyId(), TEST_STUDY_ID);
        assertEquals(capturedRecord.getUserId(), TEST_USER_ID);
        assertEquals(capturedRecord.getInstanceGuid(), GUID);
        assertEquals(capturedRecord.getEventTimestamp().toString(), TIMESTAMP.toString());
        assertEquals(capturedRecord.getUploadedOn(), TIMESTAMP.plusHours(1));
        assertEquals(capturedRecord.getUploadIds().size(), 1);
        assertTrue(capturedRecord.getUploadIds().contains(UPLOAD_ID_1));
    }
    
    @Test
    public void updateAdherenceWithUploadInfo_successfullyUpdateExistingRecord() throws JsonProcessingException {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(UPLOAD_ID_1);
        upload.setCompletedOn(TIMESTAMP.plusHours(1).getMillis());
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(constructMetadata(METADATA_KEY_INSTANCE_GUID, GUID,
                METADATA_KEY_EVENT_TIMESTAMP, TIMESTAMP.toString(),
                METADATA_KEY_STARTED_ON, TIMESTAMP.plusHours(1).toString()));
        
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthcode:" + HEALTH_CODE))
                .thenReturn(Optional.of(TEST_USER_ID));
        
        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setScheduleGuid(SCHEDULE_GUID);
        
        when(mockScheduleService.getTimelineMetadata(GUID)).thenReturn(Optional.of(timelineMetadata));
        
        when(mockStudyService.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID))
                .thenReturn(ImmutableList.of(TEST_STUDY_ID));
        
        AdherenceRecord record1 = new AdherenceRecord();
        record1.setAppId(TEST_APP_ID);
        record1.setStudyId(TEST_STUDY_ID);
        record1.setUserId(TEST_USER_ID);
        record1.setInstanceGuid(GUID);
        record1.setEventTimestamp(TIMESTAMP);
        record1.setStartedOn(TIMESTAMP.plusHours(1));
    
        AdherenceRecord record2 = new AdherenceRecord();
        record2.setAppId(TEST_APP_ID);
        record2.setStudyId(TEST_STUDY_ID);
        record2.setUserId(TEST_USER_ID);
        record2.setInstanceGuid(GUID);
        record2.setEventTimestamp(TIMESTAMP.plusHours(2));
        record2.setUploadedOn(TIMESTAMP.plusHours(1));
        record2.addUploadId(UPLOAD_ID_2);
        
        PagedResourceList<AdherenceRecord> searchResult = new PagedResourceList<>(
                ImmutableList.of(record2, record1), 2);
        
        when(mockAdherenceService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(searchResult);
        
        svc.updateAdherenceWithUploadInfo(TEST_APP_ID, upload);
        
        // Verify the new adherence records are sent for update.
        verify(mockAdherenceService).updateAdherenceRecords(eq(TEST_APP_ID), adherenceRecordListCaptor.capture());
        
        assertNotNull(adherenceRecordListCaptor);
        assertNotNull(adherenceRecordListCaptor.getValue());
        AdherenceRecordList capturedAdherenceRecordList = adherenceRecordListCaptor.getValue();
        assertNotNull(capturedAdherenceRecordList.getRecords());
        List<AdherenceRecord> capturedRecordList = capturedAdherenceRecordList.getRecords();
        assertEquals(capturedRecordList.size(), 1);
        
        AdherenceRecord capturedRecord1 = capturedRecordList.get(0);
        assertEquals(capturedRecord1.getAppId(), TEST_APP_ID);
        assertEquals(capturedRecord1.getStudyId(), TEST_STUDY_ID);
        assertEquals(capturedRecord1.getUserId(), TEST_USER_ID);
        assertEquals(capturedRecord1.getInstanceGuid(), GUID);
        assertEquals(capturedRecord1.getEventTimestamp().toString(), TIMESTAMP.toString());
        assertEquals(capturedRecord1.getStartedOn().toString(), TIMESTAMP.plusHours(1).toString());
        assertEquals(capturedRecord1.getUploadedOn(), TIMESTAMP.plusHours(1));
        assertNotNull(capturedRecord1.getUploadIds());
        assertTrue(capturedRecord1.getUploadIds().contains(UPLOAD_ID_1));
    }
    
    UploadRequest constructUploadRequest() {
        return new UploadRequest.Builder().withName("oneUpload").withContentLength(1048L)
                .withContentMd5("AAAAAAAAAAAAAAAAAAAAAA==")
                .withContentType("application/binary").build();
    }
    
    ObjectNode constructMetadata(String key1, String value1, String key2, 
                                 String value2, String key3, String value3) throws JsonProcessingException {
        String jsonText = "{\"" + key1 + "\":\"" + value1 + "\",\"" + key2 + "\":\"" + value2 + "\",\"" +
                key3 + "\":\"" + value3 + "\"}";
        return (ObjectNode) BridgeObjectMapper.get().readTree(jsonText);
    }
}
