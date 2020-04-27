package org.sagebionetworks.bridge.upload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Nonnull;

import com.google.common.io.ByteStreams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.services.UploadArchiveService;

/**
 * Validation handler for decrypting the upload. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getDataFile}, decrypts it, and writes the decrypted
 * data to {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setDecryptedDataFile }.
 */
@Component
public class DecryptHandler implements UploadValidationHandler {
    private FileHelper fileHelper;
    private UploadArchiveService uploadArchiveService;

    /** File helper, used to create the decrypted file and to get file streams. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** Upload archive service, which handles decrypting and unzipping of files. This is configured by Spring. */
    @Autowired
    public void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        // Temp file name in the form "[uploadId].zip"
        String outputFilename = context.getUploadId() + ".zip";
        File outputFile = fileHelper.newFile(context.getTempDir(), outputFilename);

        // Decrypt - Stream from input file to output file.
        // Note: Neither FileHelper nor CmsEncryptor introduce any buffering. Since we're creating and closing streams,
        // it's our responsibility to add the buffered stream.
        try (InputStream inputFileStream = getBufferedInputStream(fileHelper.getInputStream(context.getDataFile()));
             InputStream decryptedInputFileStream = uploadArchiveService.decrypt(context.getAppId(),
                     inputFileStream);
             OutputStream outputFileStream = new BufferedOutputStream(fileHelper.getOutputStream(outputFile))) {
            ByteStreams.copy(decryptedInputFileStream, outputFileStream);
        } catch (IOException ex) {
            throw new UploadValidationException("Error decrypting file: " + ex.getMessage(), ex);
        }

        // Set file in context.
        context.setDecryptedDataFile(outputFile);
    }

    // This helper method wraps a stream inside a buffered stream. It exists because our unit tests use
    // InMemoryFileHelper, which uses a ByteArrayInputStream, which ignores closing. But in Prod, we need to wrap it in
    // a BufferedInputStream because the files can get big, and a closed BufferedInputStream breaks unit tests.
    //
    // Note that OutputStream has no such limitation, since InMemoryFileHelper intercepts the output.
    InputStream getBufferedInputStream(InputStream inputStream) {
        return new BufferedInputStream(inputStream);
    }
}
