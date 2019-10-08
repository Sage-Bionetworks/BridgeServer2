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
        metadata.setDescription("A description");
        
        Validate.entityThrowingException(INSTANCE, metadata);
    }
    
    @Test
    public void nameRequired() {
        FileMetadata metadata = new FileMetadata();
        
        assertValidatorMessage(INSTANCE, metadata, "name", "is required");
    }
    
}
