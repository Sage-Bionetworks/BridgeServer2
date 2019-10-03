package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.FileRevisionValidator.INSTANCE;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;

public class FileRevisionValidatorTest {

    @Test
    public void validates() { 
        FileRevision revision = new FileRevision();
        revision.setName("name");
        revision.setMimeType("text/plain");
        
        Validate.entityThrowingException(INSTANCE, revision);
    }
    
    @Test
    public void nameRequired() {
        FileRevision revision = new FileRevision();
        
        assertValidatorMessage(INSTANCE, revision, "name", "is required");
    }

    @Test
    public void mimeTypeRequired() {
        FileRevision revision = new FileRevision();
        
        assertValidatorMessage(INSTANCE, revision, "mimeType", "is required");
    }
}
