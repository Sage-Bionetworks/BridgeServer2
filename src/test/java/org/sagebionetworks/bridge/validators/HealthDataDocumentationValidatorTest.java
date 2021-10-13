package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

public class HealthDataDocumentationValidatorTest {
    private static final String TITLE = "test-title";
    private static final String DOCUMENTATION = "test-documentation";
    private static final String EXCEEDS_MAX_SIZE_DOCUMENTATION = new String(new char[102500]);

    @Test
    public void validHealthDataDocumentation() {
        Validate.entityThrowingException(HealthDataDocumentationValidator.INSTANCE, makeValidHealthDataDocumentation());
    }

    @Test
    public void nullTitle() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setTitle(null);
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "title", "cannot be null");
    }

    @Test
    public void emptyTitle() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setTitle("");
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "title", "cannot be an empty string");
    }

    @Test
    public void nullParentId() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setParentId(null);
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "parentId", "cannot be null");
    }

    @Test
    public void emptyParentId() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setParentId("");
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "parentId", "cannot be an empty string");
    }

    @Test
    public void nullIdentifier() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setIdentifier(null);
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "identifier", "cannot be null");
    }

    @Test
    public void emptyIdentifier() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setIdentifier("");
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "identifier", "cannot be an empty string");
    }

    @Test
    public void nullDocumentation() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setDocumentation(null);
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "documentation", "cannot be null");
    }

    @Test
    public void emptyDocumentation() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setDocumentation("");
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "documentation", "cannot be an empty string");
    }

    @Test
    public void documentationExceedsMaximumSize() {
        HealthDataDocumentation doc = makeValidHealthDataDocumentation();
        doc.setDocumentation(EXCEEDS_MAX_SIZE_DOCUMENTATION);
        assertValidatorMessage(HealthDataDocumentationValidator.INSTANCE, doc, "documentation", "exceeds the maximum allowed size");
    }

    private static HealthDataDocumentation makeValidHealthDataDocumentation() {
        HealthDataDocumentation doc = HealthDataDocumentation.create();
        doc.setTitle(TITLE);
        doc.setParentId(TEST_APP_ID);
        doc.setIdentifier(IDENTIFIER);
        doc.setDocumentation(DOCUMENTATION);
        return doc;
    }
}