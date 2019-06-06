package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.common.collect.ImmutableList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;

public class UploadServicePollStatusTest {
    private static final String UPLOAD_ID = "test-upload";

    private UploadService svc;

    @BeforeMethod
    public void setup() {
        // Spy service, so we can mock a call to getValidationStatus() instead of tightly coupling to that logic.
        svc = spy(new UploadService());

        // Set sleep time to 10ms and max iterations to 2, so we don't have to wait that long.
        svc.setPollValidationStatusMaxIterations(2);
        svc.setPollValidationStatusSleepMillis(10);
    }

    @Test
    public void firstTry() {
        doReturn(makeValidationStatus(UploadStatus.SUCCEEDED)).when(svc).getUploadValidationStatus(UPLOAD_ID);
        UploadValidationStatus validationStatus = svc.pollUploadValidationStatusUntilComplete(UPLOAD_ID);
        assertEquals(validationStatus.getStatus(), UploadStatus.SUCCEEDED);
        verify(svc, times(1)).getUploadValidationStatus(UPLOAD_ID);
    }

    @Test
    public void secondTry() {
        UploadValidationStatus inProgressStatus = makeValidationStatus(UploadStatus.VALIDATION_IN_PROGRESS);
        UploadValidationStatus succeededStatus = makeValidationStatus(UploadStatus.SUCCEEDED);

        doReturn(inProgressStatus).doReturn(succeededStatus).when(svc).getUploadValidationStatus(UPLOAD_ID);
        UploadValidationStatus validationStatus = svc.pollUploadValidationStatusUntilComplete(UPLOAD_ID);
        assertEquals(validationStatus.getStatus(), UploadStatus.SUCCEEDED);
        verify(svc, times(2)).getUploadValidationStatus(UPLOAD_ID);
    }

    @Test
    public void timeout() {
        doReturn(makeValidationStatus(UploadStatus.VALIDATION_IN_PROGRESS)).when(svc).getUploadValidationStatus(
                UPLOAD_ID);

        try {
            svc.pollUploadValidationStatusUntilComplete(UPLOAD_ID);
            fail("expected exception");
        } catch (BridgeServiceException ex) {
            assertEquals(ex.getMessage(), "Timeout polling validation status for upload " + UPLOAD_ID);
        }
        verify(svc, times(2)).getUploadValidationStatus(UPLOAD_ID);
    }

    private UploadValidationStatus makeValidationStatus(UploadStatus uploadStatus) {
        return new UploadValidationStatus.Builder().withId(UPLOAD_ID).withMessageList(ImmutableList.of())
                .withStatus(uploadStatus).build();
    }
}
