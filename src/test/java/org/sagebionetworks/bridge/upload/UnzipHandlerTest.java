package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.verifyZeroInteractions;
import static org.testng.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertSame;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.UploadArchiveService;

public class UnzipHandlerTest {
    private static final String ZIPPED_FILE_NAME = "test.zip";
    private static final byte[] ZIPPED_FILE_DUMMY_CONTENT = "zipped test data".getBytes(Charsets.UTF_8);

    private UploadValidationContext ctx;
    private InMemoryFileHelper inMemoryFileHelper;
    private Upload upload;

    @Mock
    private UploadArchiveService mockSvc;

    @InjectMocks
    private UnzipHandler handler;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Set up File Helper and input file.
        inMemoryFileHelper = new InMemoryFileHelper();
        File tmpDir = inMemoryFileHelper.createTempDir();
        File zippedDataFile = inMemoryFileHelper.newFile(tmpDir, ZIPPED_FILE_NAME);
        inMemoryFileHelper.writeBytes(zippedDataFile, ZIPPED_FILE_DUMMY_CONTENT);
        handler.setFileHelper(inMemoryFileHelper);

        // inputs
        upload = Upload.create();
        upload.setFilename(ZIPPED_FILE_NAME);

        ctx = new UploadValidationContext();
        ctx.setUpload(upload);
        ctx.setTempDir(tmpDir);
        ctx.setDecryptedDataFile(zippedDataFile);
    }

    @Test
    public void test() throws Exception {
        // The handler is a simple pass-through to the UploadArchiveService, so just test that execution flows through
        // to the service as expected.

        // mock UploadArchiveService
        Map<String, byte[]> mockUnzippedDataMap = ImmutableMap.of(
                "foo", "foo data".getBytes(Charsets.UTF_8),
                "bar", "bar data".getBytes(Charsets.UTF_8),
                "baz", "baz data".getBytes(Charsets.UTF_8));

        doAnswer(invocation -> {
            Function<String, OutputStream> entryNameToOutputStream = invocation.getArgument(1);
            BiConsumer<String, OutputStream> outputStreamFinalizer = invocation.getArgument(2);

            // Mimic unzip by writing the unzipped data to the output stream.
            for (Map.Entry<String, byte[]> oneUnzippedDataEntry : mockUnzippedDataMap.entrySet()) {
                String entryName = oneUnzippedDataEntry.getKey();
                byte[] mockContent = oneUnzippedDataEntry.getValue();

                OutputStream unzipOutputStream = entryNameToOutputStream.apply(entryName);
                unzipOutputStream.write(mockContent);
                outputStreamFinalizer.accept(entryName, unzipOutputStream);
            }

            // Required return.
            return null;
        }).when(mockSvc).unzip(any(), any(), any());

        // execute and validate
        handler.handle(ctx);
        Map<String, File> unzippedFileMap = ctx.getUnzippedDataFileMap();
        assertEquals(unzippedFileMap.size(), mockUnzippedDataMap.size());
        for (String oneUnzippedFileName : mockUnzippedDataMap.keySet()) {
            File unzippedFile = unzippedFileMap.get(oneUnzippedFileName);
            byte[] unzippedFileContent = inMemoryFileHelper.getBytes(unzippedFile);
            assertEquals(unzippedFileContent, mockUnzippedDataMap.get(oneUnzippedFileName));
        }

        // verify stream passed into mockSvc
        ArgumentCaptor<InputStream> zippedFileInputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(mockSvc).unzip(zippedFileInputStreamCaptor.capture(), any(), any());

        InputStream zippedFileInputStream = zippedFileInputStreamCaptor.getValue();
        byte[] zippedFileInputStreamContent = ByteStreams.toByteArray(zippedFileInputStream);
        assertEquals(zippedFileInputStreamContent, ZIPPED_FILE_DUMMY_CONTENT);
    }

    @Test
    public void notZipped() throws Exception {
        // Set up inputs and execute.
        upload.setZipped(false);
        handler.handle(ctx);

        // Verify output is the same as input.
        Map<String, File> unzippedDataFileMap = ctx.getUnzippedDataFileMap();
        assertEquals(unzippedDataFileMap.size(), 1);
        assertSame(unzippedDataFileMap.get(upload.getFilename()), ctx.getDecryptedDataFile());

        // We hever call the unzip service.
        verifyZeroInteractions(mockSvc);
    }
}
