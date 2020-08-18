package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ParticipantFileValidator.INSTANCE;

import org.sagebionetworks.bridge.dynamodb.DynamoParticipantFile;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.Test;

public class ParticipantFileValidatorTest {

    @Test
    public void validates() {
        ParticipantFile file = new DynamoParticipantFile("user", "file");
        file.setMimeType("dummy");
        file.setAppId("api");

        Validate.entityThrowingException(INSTANCE, file);
    }

    @Test
    public void fileIdRequired() {
        ParticipantFile file = new DynamoParticipantFile("user", "file");
        file.setMimeType("dummy");

        assertValidatorMessage(INSTANCE, file, "appId", "is required");
    }
}
