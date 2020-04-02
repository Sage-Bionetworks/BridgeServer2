package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import com.google.common.base.Charsets;

import com.google.common.io.ByteStreams;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class DecryptHandlerTest {
    @Test
    public void test() throws Exception {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // Set up file helper.
        InMemoryFileHelper fileHelper = new InMemoryFileHelper();
        File tmpDir = fileHelper.createTempDir();

        File dataFile = fileHelper.newFile(tmpDir, "data-file");
        byte[] dataFileContent = "encrypted test data".getBytes(Charsets.UTF_8);
        fileHelper.writeBytes(dataFile, dataFileContent);

        // inputs
        DynamoStudy study = TestUtils.getValidStudy(DecryptHandlerTest.class);

        UploadValidationContext ctx = new UploadValidationContext();
        ctx.setStudy(study.getIdentifier());
        ctx.setTempDir(tmpDir);
        ctx.setDataFile(dataFile);

        // mock UploadArchiveService
        UploadArchiveService mockSvc = mock(UploadArchiveService.class);
        when(mockSvc.decrypt(eq(study.getIdentifier()), any(InputStream.class))).thenReturn(new ByteArrayInputStream(
                "decrypted test data".getBytes(Charsets.UTF_8)));

        // set up test handler
        DecryptHandler handler = spy(new DecryptHandler());
        handler.setFileHelper(fileHelper);
        handler.setUploadArchiveService(mockSvc);

        // Don't actually buffer the input stream, as this breaks the test.
        doAnswer(invocation -> invocation.getArgument(0)).when(handler).getBufferedInputStream(any());

        // execute and validate
        handler.handle(ctx);
        byte[] decryptedContent = fileHelper.getBytes(ctx.getDecryptedDataFile());
        assertEquals(new String(decryptedContent, Charsets.UTF_8), "decrypted test data");

        // Verify the correct file data was passed into the decryptor.
        ArgumentCaptor<InputStream> encryptedInputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(mockSvc).decrypt(eq(study.getIdentifier()), encryptedInputStreamCaptor.capture());
        InputStream encryptedInputStream = encryptedInputStreamCaptor.getValue();
        assertEquals(ByteStreams.toByteArray(encryptedInputStream), dataFileContent);
    }
}
