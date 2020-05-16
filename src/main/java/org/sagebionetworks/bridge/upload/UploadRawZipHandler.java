package org.sagebionetworks.bridge.upload;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;

/** Uploads decrypted zip file to attachments and sets the record's raw data attachment ID appropriately. */
@Component
public class UploadRawZipHandler implements UploadValidationHandler {
    // Package-scoped for unit tests.
    static final String RAW_ATTACHMENT_SUFFIX = "-raw.zip";

    private UploadFileHelper uploadFileHelper;

    /** Upload File Helper, used to submit raw zip file as an attachment. */
    @Autowired
    public final void setUploadFileHelper(UploadFileHelper uploadFileHelper) {
        this.uploadFileHelper = uploadFileHelper;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(UploadValidationContext context) throws UploadValidationException {
        String rawDataAttachmentId = context.getUploadId();

        Upload upload = context.getUpload();
        if (upload.isZipped()) {
            // Attachment ID is "[uploadId]-raw.zip".
            rawDataAttachmentId += RAW_ATTACHMENT_SUFFIX;
        } else {
            // For a single file upload, use the name specified by the upload request.
            rawDataAttachmentId += '-' + upload.getFilename();
        }

        // Upload raw data as an attachment.
        try {
            uploadFileHelper.uploadFileAsAttachment(rawDataAttachmentId, context.getDecryptedDataFile());
        } catch (IOException ex) {
            throw new UploadValidationException("Error upload raw data zip for upload " + context.getUploadId());
        }

        HealthDataRecord record = context.getHealthDataRecord();
        record.setRawDataAttachmentId(rawDataAttachmentId);
    }
}
