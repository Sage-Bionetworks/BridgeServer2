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
    private static final String UPLOAD_ID = "my-upload";
    private static final String EXPECTED_RAW_DATA_ATTACHMENT_ID = UPLOAD_ID +
            UploadRawZipHandler.RAW_ATTACHMENT_SUFFIX;

    private UploadValidationContext context;
    private UploadRawZipHandler handler;
    private File mockDecryptedFile;
    private UploadFileHelper mockUploadFileHelper;
    private HealthDataRecord record;

    @BeforeMethod
    public void before() {
        // Set up mocks and handler.
        mockUploadFileHelper = mock(UploadFileHelper.class);
        handler = new UploadRawZipHandler();
        handler.setUploadFileHelper(mockUploadFileHelper);

        // Set up context. We only read the upload ID, decrypted data file (mock), and the record.
        context = new UploadValidationContext();

        Upload upload = Upload.create();
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
        verify(mockUploadFileHelper).uploadFileAsAttachment(EXPECTED_RAW_DATA_ATTACHMENT_ID, mockDecryptedFile);
        assertEquals(record.getRawDataAttachmentId(), EXPECTED_RAW_DATA_ATTACHMENT_ID);
    }

    @Test(expectedExceptions = UploadValidationException.class)
    public void errorCase() throws Exception {
        // Mock uploadFileHelper to throw.
        doThrow(IOException.class).when(mockUploadFileHelper).uploadFileAsAttachment(EXPECTED_RAW_DATA_ATTACHMENT_ID,
                mockDecryptedFile);

        // Execute (throws exception).
        handler.handle(context);
    }
}
