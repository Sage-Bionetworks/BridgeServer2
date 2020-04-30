package org.sagebionetworks.bridge.services;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.upload.UploadValidationTask;
import org.sagebionetworks.bridge.upload.UploadValidationTaskFactory;

/** Service handler for upload validation. */
@Component
public class UploadValidationService {
    private ExecutorService asyncExecutorService;
    private UploadValidationTaskFactory taskFactory;

    /** Async thread pool. This is configured by Spring. */
    @Resource(name = "asyncExecutorService")
    public void setAsyncExecutorService(ExecutorService asyncExecutorService) {
        this.asyncExecutorService = asyncExecutorService;
    }

    /** Task factory. This is configured by Spring. */
    @Autowired
    public void setTaskFactory(UploadValidationTaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    /**
     * <p>
     * Kick off upload validation. Since upload validation can take some time, we handle this asynchronously. This
     * method returns immediately. Call UploadService.getUpload() to check for validation status and messages.
     * </p>
     * <p>
     * App and user comes from the controller and upload comes from UploadService.getUpload(), so none of the fields
     * are user input, so validation is not needed.
     * </p>
     *
     * @param appId
     *         app this upload lives in
     * @param upload
     *         upload metadata object for the upload
     */
    public void validateUpload(@Nonnull String appId, @Nonnull Upload upload) {
        UploadValidationTask task = taskFactory.newTask(appId, upload);
        asyncExecutorService.execute(task);
    }
}
