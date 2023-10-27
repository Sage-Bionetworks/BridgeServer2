package org.sagebionetworks.bridge.upload;

import java.io.File;
import javax.annotation.Nonnull;

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
        if (!context.getUpload().isEncrypted()) {
            // Shortcut: Input file is output file.
            context.setDecryptedDataFile(context.getDataFile());
            return;
        }

        // Temp file name in the form "[uploadId].zip"
        String outputFilename = context.getUploadId() + ".zip";
        File outputFile = fileHelper.newFile(context.getTempDir(), outputFilename);

        // Decrypt - Stream from input file to output file.
        uploadArchiveService.decrypt(context.getAppId(), context.getDataFile(), outputFile);

        // Set file in context.
        context.setDecryptedDataFile(outputFile);
    }
}
