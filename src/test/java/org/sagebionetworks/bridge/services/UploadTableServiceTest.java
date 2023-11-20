package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadTableJobDao;
import org.sagebionetworks.bridge.dao.UploadTableRowDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.upload.UploadCsvRequest;
import org.sagebionetworks.bridge.upload.UploadTableJob;
import org.sagebionetworks.bridge.upload.UploadTableJobGuidHolder;
import org.sagebionetworks.bridge.upload.UploadTableJobResult;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

public class UploadTableServiceTest {
    private static final String JOB_GUID = "test-job-guid";
    private static final String OLD_JOB_GUID = "old-job-guid";
    private static final DateTime MOCK_NOW = DateTime.parse("2018-05-23T14:18:36.026Z");
    private static final DateTime EXPIRES_ON = MOCK_NOW.plusDays(UploadTableService.EXPIRATION_IN_DAYS);
    private static final String RAW_DATA_BUCKET = "raw-data-bucket";
    private static final String RECORD_ID = "test-record";
    private static final String S3_KEY = "dummy-s3-key";
    private static final String S3_URL = "https://example.com/dummy-s3-bucket/dummy-s3-key";
    private static final String WORKER_QUEUE_URL = "http://example.com/dummy-sqs-url";

    @Mock
    private AmazonS3 mockS3Client;

    @Mock
    private AmazonSQS mockSqsClient;

    @Mock
    private StudyService mockStudyService;

    @Mock
    private UploadService mockUploadService;

    @Mock
    private UploadTableJobDao mockUploadTableJobDao;

    @Mock
    private UploadTableRowDao mockUploadTableRowDao;

    @InjectMocks
    @Spy
    private UploadTableService service;

    @BeforeClass
    public static void beforeClass() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW.getMillis());
    }

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        doReturn(JOB_GUID).when(service).generateGuid();

        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(UploadTableService.CONFIG_KEY_RAW_HEALTH_DATA_BUCKET)).thenReturn(RAW_DATA_BUCKET);
        when(mockConfig.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL)).thenReturn(WORKER_QUEUE_URL);
        service.setConfig(mockConfig);
    }

    @AfterClass
    public static void afterClass() {
        DateTimeUtils.setCurrentMillisSystem();
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void getUploadTableJobResult_PermissionsCheck() {
        setRequestContextWithRole(ORG_ADMIN);
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(makeValidJob()));
        service.getUploadTableJobResult(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadTableJobResult_NotFound() {
        setRequestContextWithRole(DEVELOPER);
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.empty());
        service.getUploadTableJobResult(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadTableJobResult_WrongApp() {
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = makeValidJob();
        job.setAppId("wrong-app");
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(job));

        service.getUploadTableJobResult(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadTableJobResult_WrongStudy() {
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = makeValidJob();
        job.setStudyId("wrong-study");
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(job));

        service.getUploadTableJobResult(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test
    public void getUploadTableJobResult_Failed() {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = makeValidJob();
        job.setStatus(UploadTableJob.Status.FAILED);
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(job));

        // Execute and verify.
        UploadTableJobResult jobResult = service.getUploadTableJobResult(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, JOB_GUID);
        assertEquals(jobResult.getJobGuid(), JOB_GUID);
        assertEquals(jobResult.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(jobResult.getRequestedOn(), TestConstants.TIMESTAMP);
        assertEquals(jobResult.getStatus(), UploadTableJob.Status.FAILED);
        assertNull(jobResult.getUrl());
        assertNull(jobResult.getExpiresOn());

        // Verify no calls to S3.
        verifyZeroInteractions(mockS3Client);
    }

    @Test
    public void getUploadTableJobResult_Success() throws MalformedURLException {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        when(mockS3Client.generatePresignedUrl(any())).thenReturn(new URL(S3_URL));

        UploadTableJob job = makeValidJob();
        job.setStatus(UploadTableJob.Status.SUCCEEDED);
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(job));

        // Execute and verify.
        UploadTableJobResult jobResult = service.getUploadTableJobResult(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, JOB_GUID);
        assertEquals(jobResult.getJobGuid(), JOB_GUID);
        assertEquals(jobResult.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(jobResult.getRequestedOn(), TestConstants.TIMESTAMP);
        assertEquals(jobResult.getStatus(), UploadTableJob.Status.SUCCEEDED);
        assertEquals(jobResult.getUrl(), S3_URL);
        assertEquals(jobResult.getExpiresOn().getMillis(), EXPIRES_ON.getMillis());

        // Verify call to S3.
        ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor = ArgumentCaptor.forClass(
                GeneratePresignedUrlRequest.class);
        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), RAW_DATA_BUCKET);
        assertEquals(request.getKey(), S3_KEY);
        assertEquals(request.getMethod(), HttpMethod.GET);
        assertEquals(request.getExpiration(), EXPIRES_ON.toDate());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void listUploadTableJobsForStudy_PermissionsCheck() {
        setRequestContextWithRole(ORG_ADMIN);
        service.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, 0,
                10);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "start cannot be negative")
    public void listUploadTableJobsForStudy_NegativeStart() {
        setRequestContextWithRole(DEVELOPER);
        service.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, -1,
                10);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "pageSize must be at least " + BridgeConstants.API_MINIMUM_PAGE_SIZE)
    public void listUploadTableJobsForStudy_PageSizeTooSmall() {
        setRequestContextWithRole(DEVELOPER);
        service.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, 0,
                BridgeConstants.API_MINIMUM_PAGE_SIZE - 1);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "pageSize must be at most " + BridgeConstants.API_MAXIMUM_PAGE_SIZE)
    public void listUploadTableJobsForStudy_PageSizeTooLarge() {
        setRequestContextWithRole(DEVELOPER);
        service.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, 0,
                BridgeConstants.API_MAXIMUM_PAGE_SIZE + 1);
    }

    @Test
    public void listUploadTableJobsForStudy_NormalCase() {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = UploadTableJob.create();
        PagedResourceList<UploadTableJob> jobList = new PagedResourceList<>(ImmutableList.of(job), 1);
        when(mockUploadTableJobDao.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 10, 20)).thenReturn(jobList);

        // Execute and verify.
        List<UploadTableJob> result = service.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 10, 20).getItems();
        assertEquals(result.size(), 1);
        assertSame(result.get(0), job);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void requestUploadTableForStudy_PermissionsCheck() {
        setRequestContextWithRole(ORG_ADMIN);
        service.requestUploadTableForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);
    }

    @Test
    public void requestUploadTableForStudy_PreviousJobTooRecent() {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = makeValidJob();
        job.setJobGuid(OLD_JOB_GUID);
        job.setRequestedOn(MOCK_NOW.minusMinutes(UploadTableService.DEDUPE_WINDOW_MINUTES - 1));
        PagedResourceList<UploadTableJob> jobList = new PagedResourceList<>(ImmutableList.of(job), 1);
        when(mockUploadTableJobDao.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 0, 1)).thenReturn(jobList);

        // Execute and verify.
        UploadTableJobGuidHolder jobGuidHolder = service.requestUploadTableForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertEquals(jobGuidHolder.getJobGuid(), OLD_JOB_GUID);
    }

    @Test
    public void requestUploadTableForStudy_NoNewUploads() {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = makeValidJob();
        job.setJobGuid(OLD_JOB_GUID);
        PagedResourceList<UploadTableJob> jobList = new PagedResourceList<>(ImmutableList.of(job), 1);
        when(mockUploadTableJobDao.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 0, 1)).thenReturn(jobList);

        when(mockUploadService.getAppUploads(TestConstants.TEST_APP_ID, job.getRequestedOn(), MOCK_NOW, 1, null))
                .thenReturn(new ForwardCursorPagedResourceList<>(ImmutableList.of(), null));

        // Execute and verify.
        UploadTableJobGuidHolder jobGuidHolder = service.requestUploadTableForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertEquals(jobGuidHolder.getJobGuid(), OLD_JOB_GUID);
    }

    @Test
    public void requestUploadTableForStudy_NoPreviousJob() throws JsonProcessingException {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        PagedResourceList<UploadTableJob> jobList = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockUploadTableJobDao.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 0, 1)).thenReturn(jobList);

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute and verify.
        UploadTableJobGuidHolder jobGuidHolder = service.requestUploadTableForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertEquals(jobGuidHolder.getJobGuid(), JOB_GUID);

        // Verify call to the Job DAO.
        ArgumentCaptor<UploadTableJob> createdJobCaptor = ArgumentCaptor.forClass(UploadTableJob.class);
        verify(mockUploadTableJobDao).saveUploadTableJob(createdJobCaptor.capture());
        UploadTableJob createdJob = createdJobCaptor.getValue();
        assertEquals(createdJob.getJobGuid(), JOB_GUID);
        assertEquals(createdJob.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(createdJob.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(createdJob.getRequestedOn().getMillis(), MOCK_NOW.getMillis());
        assertEquals(createdJob.getStatus(), UploadTableJob.Status.IN_PROGRESS);

        // Verify call to SQS.
        ArgumentCaptor<String> requestJsonTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSqsClient).sendMessage(eq(WORKER_QUEUE_URL), requestJsonTextCaptor.capture());

        String requestJsonText = requestJsonTextCaptor.getValue();
        WorkerRequest workerRequest = BridgeObjectMapper.get().readValue(requestJsonText, WorkerRequest.class);
        assertEquals(workerRequest.getService(), UploadTableService.WORKER_NAME_UPLOAD_CSV);

        // Need to convert WorkerRequest.body again, because it doesn't carry inherent typing information. This is
        // fine, since outside of unit tests, we never actually need to deserialize it.
        UploadCsvRequest uploadCsvRequest = BridgeObjectMapper.get().convertValue(workerRequest.getBody(),
                UploadCsvRequest.class);
        assertEquals(uploadCsvRequest.getJobGuid(), JOB_GUID);
        assertEquals(uploadCsvRequest.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(uploadCsvRequest.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertTrue(uploadCsvRequest.isIncludeTestData());
    }

    @Test
    public void requestUploadTableForStudy_NewJob() {
        // Set up mocks.
        setRequestContextWithRole(DEVELOPER);

        UploadTableJob job = makeValidJob();
        job.setJobGuid(OLD_JOB_GUID);
        PagedResourceList<UploadTableJob> jobList = new PagedResourceList<>(ImmutableList.of(job), 1);
        when(mockUploadTableJobDao.listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 0, 1)).thenReturn(jobList);

        when(mockUploadService.getAppUploads(TestConstants.TEST_APP_ID, job.getRequestedOn(), MOCK_NOW, 1, null))
                .thenReturn(new ForwardCursorPagedResourceList<>(ImmutableList.of(new UploadView.Builder().build()), null));

        // Mock SQS. Return type doesn't actually matter except for logs, but we don't want it to be null.
        when(mockSqsClient.sendMessage(any(), any())).thenReturn(new SendMessageResult());

        // Execute and verify.
        UploadTableJobGuidHolder jobGuidHolder = service.requestUploadTableForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertEquals(jobGuidHolder.getJobGuid(), JOB_GUID);

        // Verify call to back-ends. We don't care about the parameters. This was tested in the previous test.
        verify(mockUploadTableJobDao).saveUploadTableJob(any());
        verify(mockSqsClient).sendMessage(eq(WORKER_QUEUE_URL), any());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadTableJobForWorker_NotFound() {
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.empty());
        service.getUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadTableJobForWorker_WrongApp() {
        UploadTableJob job = makeValidJob();
        job.setAppId("wrong-app");
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(job));

        service.getUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadTableJobForWorker_WrongStudy() {
        UploadTableJob job = makeValidJob();
        job.setStudyId("wrong-study");
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(job));

        service.getUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test
    public void getUploadTableJobForWorker_NormalCase() {
        when(mockUploadTableJobDao.getUploadTableJob(JOB_GUID)).thenReturn(Optional.of(makeValidJob()));

        UploadTableJob job = service.getUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                JOB_GUID);
        assertEquals(job.getJobGuid(), JOB_GUID);
        assertEquals(job.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(job.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(job.getRequestedOn(), TestConstants.TIMESTAMP);
        assertEquals(job.getStatus(), UploadTableJob.Status.SUCCEEDED);
        assertEquals(job.getS3Key(), S3_KEY);
    }

    @Test
    public void updateUploadTableJobForWorker() {
        // Set up mocks. Put in the wrong app, study, and job GUID to make sure our service sets the correct values
        // from the arguments.
        UploadTableJob job = makeValidJob();
        job.setJobGuid("wrong-job-guid");
        job.setAppId("wrong-app");
        job.setStudyId("wrong-study");
        service.updateUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID, job);

        // Execute and verify.
        ArgumentCaptor<UploadTableJob> createdJobCaptor = ArgumentCaptor.forClass(UploadTableJob.class);
        verify(mockUploadTableJobDao).saveUploadTableJob(createdJobCaptor.capture());
        UploadTableJob createdJob = createdJobCaptor.getValue();
        assertEquals(createdJob.getJobGuid(), JOB_GUID);
        assertEquals(createdJob.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(createdJob.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(createdJob.getRequestedOn(), TestConstants.TIMESTAMP);
        assertEquals(createdJob.getStatus(), UploadTableJob.Status.SUCCEEDED);
        assertEquals(createdJob.getS3Key(), S3_KEY);
    }

    @Test
    public void updateUploadTableJobForWorker_RequestedOnNull() {
        // Set up mocks. Blank out requestedOn. That's the only thing we need to test.
        UploadTableJob job = makeValidJob();
        job.setRequestedOn(null);
        service.updateUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID, job);

        // Execute and verify.
        ArgumentCaptor<UploadTableJob> createdJobCaptor = ArgumentCaptor.forClass(UploadTableJob.class);
        verify(mockUploadTableJobDao).saveUploadTableJob(createdJobCaptor.capture());
        UploadTableJob createdJob = createdJobCaptor.getValue();
        assertEquals(createdJob.getRequestedOn().getMillis(), MOCK_NOW.getMillis());
    }

    private static UploadTableJob makeValidJob() {
        UploadTableJob job = UploadTableJob.create();
        job.setJobGuid(JOB_GUID);
        job.setAppId(TestConstants.TEST_APP_ID);
        job.setStudyId(TestConstants.TEST_STUDY_ID);
        job.setRequestedOn(TestConstants.TIMESTAMP);
        job.setStatus(UploadTableJob.Status.SUCCEEDED);
        job.setS3Key(S3_KEY);
        return job;
    }

    private static void setRequestContextWithRole(Roles role) {
        // We also need to include a caller user ID, because without a caller user ID, isSelf() will be true.
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(role))
                .withCallerUserId(TestConstants.TEST_USER_ID).build());

    }

    @Test
    public void deleteUploadTableRow() {
        // Execute and verify.
        service.deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
        verify(mockUploadTableRowDao).deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
    }

    @Test
    public void deleteUploadTableRow_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void getUploadTableRow() {
        // Set up mocks.
        UploadTableRow row = UploadTableRow.create();
        when(mockUploadTableRowDao.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID)).thenReturn(Optional.of(row));

        // Execute and verify.
        UploadTableRow result = service.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
        assertSame(result, row);
        verify(mockUploadTableRowDao).getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
    }

    @Test
    public void getUploadTableRow_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void getUploadTableRow_rowDoesntExist() {
        // Set up mocks.
        when(mockUploadTableRowDao.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID)).thenReturn(Optional.empty());


        // Execute - This throws.
        try {
            service.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "UploadTableRow");
        }
    }

    @Test
    public void queryUploadTableRows() {
        // Execute and verify.
        UploadTableRowQuery query = new UploadTableRowQuery();
        service.queryUploadTableRows(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, query);
        verify(mockUploadTableRowDao).queryUploadTableRows(same(query));

        // Verify that the query was updated with appId and studyId.
        assertEquals(query.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(query.getStudyId(), TestConstants.TEST_STUDY_ID);
    }

    @Test
    public void queryUploadTableRows_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.queryUploadTableRows(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                    new UploadTableRowQuery());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void queryUploadTableRows_invalidQuery() {
        // Simplest way to make an invalid query is with a negative start.
        UploadTableRowQuery query = new UploadTableRowQuery();
        query.setStart(-1);

        // Execute - This throws.
        try {
            service.queryUploadTableRows(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, query);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }
    }

    @Test
    public void saveUploadTableRow() {
        // Execute and verify.
        UploadTableRow row = makeValidRow();
        service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, row);
        verify(mockUploadTableRowDao).saveUploadTableRow(same(row));

        // Verify that the row was updated with appId and studyId and a non-null createdOn.
        assertEquals(row.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(row.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertNotNull(row.getCreatedOn());
    }

    @Test
    public void saveUploadTableRow_optionalCreatedOn() {
        // Execute and verify.
        UploadTableRow row = makeValidRow();
        row.setCreatedOn(TestConstants.CREATED_ON);
        service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, row);
        verify(mockUploadTableRowDao).saveUploadTableRow(same(row));

        // Verify that the row was updated with appId and studyId, but createdOn is unchanged.
        assertEquals(row.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(row.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(row.getCreatedOn(), TestConstants.CREATED_ON);
    }

    @Test
    public void saveUploadTableRow_studyDoesntExist() {
        // Set up mocks.
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).getStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, true);

        // Execute - This throws.
        try {
            service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, makeValidRow());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals(ex.getEntityClass(), "Study");
        }
    }

    @Test
    public void saveUploadTableRow_invalidRow() {
        // Simplest way to make an invalid ror is with a blank recordId.
        UploadTableRow row = makeValidRow();
        row.setRecordId("   ");

        // Execute - This throws.
        try {
            service.saveUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, row);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }
    }

    private static UploadTableRow makeValidRow() {
        UploadTableRow row = UploadTableRow.create();
        row.setRecordId(RECORD_ID);
        row.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        row.setHealthCode(TestConstants.HEALTH_CODE);
        return row;
    }
}
