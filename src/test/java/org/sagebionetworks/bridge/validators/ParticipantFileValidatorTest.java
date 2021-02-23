package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ParticipantFileValidator.INSTANCE;

import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.Test;

public class ParticipantFileValidatorTest {

    @Test
    public void validates() {
        ParticipantFile file = ParticipantFile.create();
        file.setUserId("user");
        file.setFileId("file");
        file.setMimeType("dummy");
        file.setAppId("api");

        Validate.entityThrowingException(INSTANCE, file);
    }

    @Test
    public void fileIdRequired() {
        ParticipantFile file = ParticipantFile.create();
        file.setUserId("user");
        file.setMimeType("dummy");
        file.setAppId("api");

        assertValidatorMessage(INSTANCE, file, "fileId", "is required");
    }

    @Test
    public void appIdRequired() {
        ParticipantFile file = ParticipantFile.create();
        file.setUserId("user");
        file.setFileId("file");
        file.setMimeType("dummy");

        assertValidatorMessage(INSTANCE, file, "appId", "is required");
    }

    @Test
    public void userIdRequired() {
        ParticipantFile file = ParticipantFile.create();
        file.setFileId("file");
        file.setMimeType("dummy");
        file.setAppId("api");

        assertValidatorMessage(INSTANCE, file, "userId", "is required");
    }
    
    @Test
    public void mimeTypeRequired() {
        ParticipantFile file = ParticipantFile.create();
        file.setUserId("user");
        file.setFileId("file");
        file.setAppId("api");

        assertValidatorMessage(INSTANCE, file, "mimeType", "is required");
    }
}
