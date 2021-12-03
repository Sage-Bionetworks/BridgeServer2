package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.TestUtils.generateStringOfLength;
import static org.sagebionetworks.bridge.TestUtils.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.validators.FileRevisionValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.files.FileRevision;

public class FileRevisionValidatorTest {

    @Test
    public void validates() { 
        FileRevision revision = new FileRevision();
        revision.setFileGuid(GUID);
        revision.setName("name");
        revision.setMimeType("text/plain");
        
        Validate.entityThrowingException(INSTANCE, revision);
    }
    
    @Test
    public void fileGuidRequired() {
        FileRevision revision = new FileRevision();
        
        assertValidatorMessage(INSTANCE, revision, "fileGuid", "is required");
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
    
    @Test
    public void stringLengthValidator_name() {
        FileRevision revision = new FileRevision();
        revision.setName(generateStringOfLength(256));
    
        assertValidatorMessage(INSTANCE, revision, "name", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidator_description() {
        FileRevision revision = new FileRevision();
        revision.setDescription(generateStringOfLength(TEXT_SIZE + 1));
        
        assertValidatorMessage(INSTANCE, revision, "description", getInvalidStringLengthMessage(TEXT_SIZE));
    }
    
    @Test
    public void stringLengthValidator_mimeType() {
        FileRevision revision = new FileRevision();
        revision.setMimeType(generateStringOfLength(256));
        
        assertValidatorMessage(INSTANCE, revision, "mimeType", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidator_uploadUrl() {
        FileRevision revision = new FileRevision();
        revision.setUploadURL(generateStringOfLength(1025));
        
        assertValidatorMessage(INSTANCE, revision, "uploadUrl", getInvalidStringLengthMessage(1024));
    }
}
