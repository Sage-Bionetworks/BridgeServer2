package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.testng.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import com.google.common.base.Charsets;

import com.google.common.io.ByteStreams;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class DecryptHandlerTest {
    private static final byte[] DATA_FILE_CONTENT = "encrypted test data".getBytes(Charsets.UTF_8);

    private UploadValidationContext ctx;
    private File dataFile;
    private InMemoryFileHelper fileHelper;
    private Upload upload;

    @Mock
    private UploadArchiveService mockSvc;

    @InjectMocks
    @Spy
    private DecryptHandler handler;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Set up file helper.
        fileHelper = new InMemoryFileHelper();
        handler.setFileHelper(fileHelper);

        File tmpDir = fileHelper.createTempDir();
        dataFile = fileHelper.newFile(tmpDir, "data-file");
        fileHelper.writeBytes(dataFile, DATA_FILE_CONTENT);

        // inputs
        upload = Upload.create();

        ctx = new UploadValidationContext();
        ctx.setAppId(TEST_APP_ID);
        ctx.setUpload(upload);
        ctx.setTempDir(tmpDir);
        ctx.setDataFile(dataFile);

        // mock UploadArchiveService
        when(mockSvc.decrypt(eq(TEST_APP_ID), any(InputStream.class))).thenReturn(new ByteArrayInputStream(
                "decrypted test data".getBytes(Charsets.UTF_8)));

        // Don't actually buffer the input stream, as this breaks the test.
        doAnswer(invocation -> invocation.getArgument(0)).when(handler).getBufferedInputStream(any());
    }

    @Test
    public void test() throws Exception {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // execute and validate
        handler.handle(ctx);
        byte[] decryptedContent = fileHelper.getBytes(ctx.getDecryptedDataFile());
        assertEquals(new String(decryptedContent, Charsets.UTF_8), "decrypted test data");

        // Verify the correct file data was passed into the decryptor.
        ArgumentCaptor<InputStream> encryptedInputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(mockSvc).decrypt(eq(TEST_APP_ID), encryptedInputStreamCaptor.capture());
        InputStream encryptedInputStream = encryptedInputStreamCaptor.getValue();
        assertEquals(ByteStreams.toByteArray(encryptedInputStream), DATA_FILE_CONTENT);
    }

    @Test
    public void notEncrypted() throws Exception {
        upload.setEncrypted(false);
        handler.handle(ctx);

        assertSame(ctx.getDecryptedDataFile(), dataFile);
        verifyZeroInteractions(mockSvc);
    }
}
