package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.files.FileDispositionType.INLINE;
import static org.sagebionetworks.bridge.validators.FileMetadataValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.files.FileMetadata;

public class FileMetadataValidatorTest {

    @Test
    public void validates() { 
        FileMetadata metadata = new FileMetadata();
        metadata.setName("name");
        metadata.setDescription("A description");
        metadata.setDisposition(INLINE);
        
        Validate.entityThrowingException(INSTANCE, metadata);
    }
    
    @Test
    public void nameRequired() {
        FileMetadata metadata = new FileMetadata();
        
        assertValidatorMessage(INSTANCE, metadata, "name", "is required");
    }
    
    @Test
    public void dispositionRequired() {
        FileMetadata metadata = new FileMetadata();
        
        assertValidatorMessage(INSTANCE, metadata, "disposition", CANNOT_BE_NULL);
    }
}
