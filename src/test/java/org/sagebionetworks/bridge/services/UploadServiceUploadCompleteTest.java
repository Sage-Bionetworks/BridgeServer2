package org.sagebionetworks.bridge.services;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.upload.UploadCompletionClient.APP;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public class UploadServiceUploadCompleteTest {
    private static final String DUPE_UPLOAD_ID = "original-upload-id";
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_UPLOAD_ID = "test-upload";

    private App app;

    @Mock
    private AppService mockAppService;

    @Mock
    private Exporter3Service mockExporter3Service;

    @Mock
    private AmazonS3 mockS3Client;

    @Mock
    private UploadDao mockUploadDao;

    @Mock
    private UploadValidationService mockUploadValidationService;

    @InjectMocks
    private UploadService svc;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Mock config. This is done separately because we need to set mock config params.
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(UploadService.CONFIG_KEY_UPLOAD_BUCKET)).thenReturn(TEST_BUCKET);
        svc.setConfig(mockConfig);

        // Mock app service. Exporter 3 is disabled for most of the tests.
        app = TestUtils.getValidApp(Exporter3ServiceTest.class);
        app.setExporter3Enabled(false);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);
    }

    @Test
    public void validationInProgress() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.VALIDATION_IN_PROGRESS);

        // execute
        svc.uploadComplete(TEST_APP_ID, APP, upload, false);

        // Verify upload DAO and validation aren't called. Can skip S3 because we don't want to over-specify our tests.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService);
    }

    @Test
    public void dupe() {
        // Dupes are detected during the Create Upload call. By the time they get here, the upload is already marked
        // with status=DUPLICATE and a dupe upload ID. Upload Complete trivially ignores these.

        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.DUPLICATE);
        upload.setDuplicateUploadId(DUPE_UPLOAD_ID);

        // execute
        svc.uploadComplete(TEST_APP_ID, APP, upload, false);

        // Similarly, verify upload DAO and validation aren't called.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService);
    }

    @Test
    public void notFoundInS3() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        AmazonS3Exception s3Ex = new AmazonS3Exception("not found");
        s3Ex.setStatusCode(404);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenThrow(s3Ex);

        // execute
        try {
            svc.uploadComplete(TEST_APP_ID, APP, upload, false);
            fail("expected exception");
        } catch (NotFoundException ex) {
            // expected exception
        }

        // Verify upload DAO and validation aren't called.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService);
    }

    @Test
    public void s3InternalError() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        AmazonS3Exception s3Ex = new AmazonS3Exception("internal server error");
        s3Ex.setStatusCode(500);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenThrow(s3Ex);

        // execute
        try {
            svc.uploadComplete(TEST_APP_ID, APP, upload, false);
            fail("expected exception");
        } catch (BridgeServiceException ex) {
            // expected exception
            assertFalse(ex instanceof NotFoundException);
        }

        // Verify upload DAO and validation aren't called.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService);
    }

    @Test
    public void uploadSuceeded() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.SUCCEEDED);

        // execute
        svc.uploadComplete(TEST_APP_ID, APP, upload, false);

        // Verify S3, upload DAO and validation aren't called.
        verifyZeroInteractions(mockUploadDao, mockUploadValidationService, mockS3Client);
    }

    @Test
    public void concurrentModification() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        ObjectMetadata mockObjMetadata = mock(ObjectMetadata.class);
        when(mockObjMetadata.getSSEAlgorithm()).thenReturn(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenReturn(mockObjMetadata);

        // mock uploadDao.uploadComplete()
        doThrow(ConcurrentModificationException.class).when(mockUploadDao).uploadComplete(APP, upload);

        // execute
        svc.uploadComplete(TEST_APP_ID, APP, upload, false);

        // Verify upload DAO and validation.
        verify(mockUploadValidationService, never()).validateUpload(any(String.class), any(Upload.class));
    }

    @Test
    public void normalCase() {
        // set up input
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // mock S3
        ObjectMetadata mockObjMetadata = mock(ObjectMetadata.class);
        when(mockObjMetadata.getSSEAlgorithm()).thenReturn(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenReturn(mockObjMetadata);

        // execute
        svc.uploadComplete(TEST_APP_ID, APP, upload, false);

        // Verify upload DAO and validation.
        verify(mockUploadDao).uploadComplete(APP, upload);
        verify(mockUploadValidationService).validateUpload(TEST_APP_ID, upload);
    }

    @Test
    public void exporter3Enabled() {
        // Enable Exporter 3.
        app.setExporter3Enabled(true);

        // Set up input.
        Upload upload = Upload.create();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.REQUESTED);

        // Mock S3.
        ObjectMetadata mockObjMetadata = mock(ObjectMetadata.class);
        when(mockObjMetadata.getSSEAlgorithm()).thenReturn(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenReturn(mockObjMetadata);

        // Execute.
        svc.uploadComplete(TEST_APP_ID, APP, upload, false);

        // Verify that we call Exporter 3.0
        verify(mockExporter3Service).completeUpload(same(app), same(upload));

        // Verify that we still call Exporter 2.0.
        verify(mockUploadDao).uploadComplete(APP, upload);
        verify(mockUploadValidationService).validateUpload(TEST_APP_ID, upload);
    }

    @Test
    public void redrive() {
        // Enable Exporter 3. It now runs on redrives.
        app.setExporter3Enabled(true);

        // Create upload that's already marked as succeeded.
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setUploadId(TEST_UPLOAD_ID);
        upload.setStatus(UploadStatus.SUCCEEDED);

        // mock S3
        ObjectMetadata mockObjMetadata = mock(ObjectMetadata.class);
        when(mockObjMetadata.getSSEAlgorithm()).thenReturn(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        when(mockS3Client.getObjectMetadata(TEST_BUCKET, TEST_UPLOAD_ID)).thenReturn(mockObjMetadata);

        // execute
        svc.uploadComplete(TEST_APP_ID, APP, upload, true);

        // Verify that we call Exporter 3.0
        verify(mockExporter3Service).completeUpload(same(app), same(upload));

        // Verify upload DAO and validation.
        verify(mockUploadDao).uploadComplete(APP, upload);
        verify(mockUploadValidationService).validateUpload(TEST_APP_ID, upload);
    }
}
