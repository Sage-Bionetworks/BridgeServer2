package org.sagebionetworks.bridge.upload;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

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
        // Upload raw data as an attachment. Attachment ID is "[uploadId]-raw.zip".
        String rawDataAttachmentId = context.getUploadId() + RAW_ATTACHMENT_SUFFIX;
        try {
            uploadFileHelper.uploadFileAsAttachment(rawDataAttachmentId, context.getDecryptedDataFile());
        } catch (IOException ex) {
            throw new UploadValidationException("Error upload raw data zip for upload " + context.getUploadId());
        }

        HealthDataRecord record = context.getHealthDataRecord();
        record.setRawDataAttachmentId(rawDataAttachmentId);
    }
}
