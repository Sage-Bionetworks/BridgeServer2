package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.TemplateRevisionValidator.INSTANCE;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

public class TemplateRevisionValidatorTest {
    
    private static final String TEMPLATE_GUID = "oneTemplateGuid";
    private static final DateTime CREATED_ON = TestConstants.TIMESTAMP;
    private static final String STORAGE_PATH = TEMPLATE_GUID + "." + CREATED_ON.toString();
    
    @Test
    public void valid() {
        TemplateRevision revision = createValidTemplate();
        Validate.entityThrowingException(INSTANCE, revision);
    }

    @Test
    public void validateTemplateGuid() {
        TemplateRevision revision = createValidTemplate();
        revision.setTemplateGuid(null);
        
        assertValidatorMessage(INSTANCE, revision, "templateGuid", "cannot be blank");
    }
    
    @Test
    public void validateCreatedOn() {
        TemplateRevision revision = createValidTemplate();
        revision.setCreatedOn(null);
        
        assertValidatorMessage(INSTANCE, revision, "createdOn", "cannot be null");
    }
    
    @Test
    public void validateCreatedBy() { 
        TemplateRevision revision = createValidTemplate();
        revision.setCreatedBy(null);
        
        assertValidatorMessage(INSTANCE, revision, "createdBy", "cannot be blank");
    }
    
    @Test
    public void validateStoragePath() { 
        TemplateRevision revision = createValidTemplate();
        revision.setStoragePath(null);
        
        assertValidatorMessage(INSTANCE, revision, "storagePath", "cannot be blank");
    }
    
    @Test
    public void validateMimeType() {
        TemplateRevision revision = createValidTemplate();
        revision.setMimeType(null);
        
        assertValidatorMessage(INSTANCE, revision, "mimeType", "cannot be null");
    }

    private TemplateRevision createValidTemplate() {
        TemplateRevision revision = TemplateRevision.create();
        revision.setTemplateGuid(TEMPLATE_GUID);
        revision.setCreatedOn(TIMESTAMP);
        revision.setCreatedBy(USER_ID);
        revision.setStoragePath(STORAGE_PATH);
        revision.setMimeType(MimeType.HTML);
        return revision;
    }
    
}
