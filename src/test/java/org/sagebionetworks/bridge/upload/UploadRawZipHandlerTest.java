package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;

public class UploadRawZipHandlerTest {
    private static final String UPLOAD_FILENAME = "filename";
    private static final String UPLOAD_ID = "my-upload";
    private static final String EXPECTED_RAW_DATA_ZIP_FILENAME = UPLOAD_ID + UploadRawZipHandler.RAW_ATTACHMENT_SUFFIX;
    private static final String EXPECTED_NON_ZIPPED_FILENAME = UPLOAD_ID + '-' + UPLOAD_FILENAME;

    private UploadValidationContext context;
    private UploadRawZipHandler handler;
    private File mockDecryptedFile;
    private UploadFileHelper mockUploadFileHelper;
    private HealthDataRecord record;
    private Upload upload;

    @BeforeMethod
    public void before() {
        // Set up mocks and handler.
        mockUploadFileHelper = mock(UploadFileHelper.class);
        handler = new UploadRawZipHandler();
        handler.setUploadFileHelper(mockUploadFileHelper);

        // Set up context. We only read the upload ID, decrypted data file (mock), and the record.
        context = new UploadValidationContext();

        upload = Upload.create();
        upload.setFilename(UPLOAD_FILENAME);
        upload.setUploadId(UPLOAD_ID);
        context.setUpload(upload);

        mockDecryptedFile = mock(File.class);
        context.setDecryptedDataFile(mockDecryptedFile);

        record = HealthDataRecord.create();
        context.setHealthDataRecord(record);
    }

    @Test
    public void test() throws Exception {
        // Execute and validate.
        handler.handle(context);
        verify(mockUploadFileHelper).uploadFileAsAttachment(EXPECTED_RAW_DATA_ZIP_FILENAME, mockDecryptedFile);
        assertEquals(record.getRawDataAttachmentId(), EXPECTED_RAW_DATA_ZIP_FILENAME);
    }

    @Test
    public void notZipped() throws Exception {
        // This is the same as the zipped case, except the filename is different.
        upload.setZipped(false);
        handler.handle(context);
        verify(mockUploadFileHelper).uploadFileAsAttachment(EXPECTED_NON_ZIPPED_FILENAME, mockDecryptedFile);
        assertEquals(record.getRawDataAttachmentId(), EXPECTED_NON_ZIPPED_FILENAME);
    }

    @Test(expectedExceptions = UploadValidationException.class)
    public void errorCase() throws Exception {
        // Mock uploadFileHelper to throw.
        doThrow(IOException.class).when(mockUploadFileHelper).uploadFileAsAttachment(EXPECTED_RAW_DATA_ZIP_FILENAME,
                mockDecryptedFile);

        // Execute (throws exception).
        handler.handle(context);
    }
}
