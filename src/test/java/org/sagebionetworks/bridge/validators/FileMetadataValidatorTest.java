package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.models.files.FileDispositionType.INLINE;
import static org.sagebionetworks.bridge.validators.FileMetadataValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

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
    
    @Test
    public void stringLengthValidation_name() {
        FileMetadata metadata = new FileMetadata();
        metadata.setName(generateStringOfLength(256));
        metadata.setDisposition(INLINE);
    
        assertValidatorMessage(INSTANCE, metadata, "name", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_description() {
        FileMetadata metadata = new FileMetadata();
        metadata.setName("name");
        metadata.setDisposition(INLINE);
        metadata.setDescription(generateStringOfLength(TEXT_SIZE + 1));
        
        assertValidatorMessage(INSTANCE, metadata, "description", getInvalidStringLengthMessage(TEXT_SIZE));
    }
}
