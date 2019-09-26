package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.FileMetadataValidator.INSTANCE;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.files.FileMetadata;

public class FileMetadataValidatorTest {

    @Test
    public void validates() { 
        FileMetadata metadata = new FileMetadata();
        metadata.setName("name");
        metadata.setMimeType("text/html");
        metadata.setDescription("A description");
        
        Validate.entityThrowingException(INSTANCE, metadata);
    }
    
    @Test
    public void nameRequired() {
        FileMetadata metadata = new FileMetadata();
        metadata.setMimeType("text/html");
        
        assertValidatorMessage(INSTANCE, metadata, "name", "is required");
    }
    
    @Test
    public void mimeTypeInvalid() {
        FileMetadata metadata = new FileMetadata();
        metadata.setName("test name");
        metadata.setMimeType("some nonsense");
        
        assertValidatorMessage(INSTANCE, metadata, "mimeType", "is not recognizable as a valid mime type");        
    }
}
