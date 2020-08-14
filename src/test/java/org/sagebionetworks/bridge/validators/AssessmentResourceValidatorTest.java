package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.RELEASE_NOTE;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.MIN_OVER_MAX_ERROR;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.RELEASE_NOTE_REVISION_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import com.google.common.collect.Lists;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;

public class AssessmentResourceValidatorTest {

    AssessmentResource resource;

    @BeforeMethod
    public void beforeMethod() {
        resource = AssessmentResourceTest.createAssessmentResource();
    }
    
    @Test
    public void assertValid() { 
        Validate.entityThrowingException(INSTANCE, resource);
    }
    
    @Test
    public void titleNull() {
        resource.setTitle(null);
        assertValidatorMessage(INSTANCE, resource, "title", CANNOT_BE_BLANK);
    }

    @Test
    public void titleBlank() {
        resource.setTitle("  ");
        assertValidatorMessage(INSTANCE, resource, "title", CANNOT_BE_BLANK);
    }

    @Test
    public void urlNull() {
        resource.setUrl(null);
        assertValidatorMessage(INSTANCE, resource, "url", CANNOT_BE_BLANK);
    }

    @Test
    public void urlBlank() {
        resource.setUrl("  ");
        assertValidatorMessage(INSTANCE, resource, "url", CANNOT_BE_BLANK);
    }

    @Test
    public void categoryNull() {
        resource.setCategory(null);
        assertValidatorMessage(INSTANCE, resource, "category", CANNOT_BE_NULL);
    }
    
    @Test
    public void creatorNull() {
        resource.setCreators(Lists.newArrayList("oneCreator", null));
        assertValidatorMessage(INSTANCE, resource, "creators[1]", CANNOT_BE_BLANK);
    }

    @Test
    public void creatorBlank() {
        resource.setCreators(Lists.newArrayList("oneCreator", "  "));
        assertValidatorMessage(INSTANCE, resource, "creators[1]", CANNOT_BE_BLANK);
    }

    @Test
    public void contributorNull() {
        resource.setContributors(Lists.newArrayList("oneContributor", null));
        assertValidatorMessage(INSTANCE, resource, "contributors[1]", CANNOT_BE_BLANK);
    }

    @Test
    public void contributorBlank() {
        resource.setContributors(Lists.newArrayList("oneContributor", "  "));
        assertValidatorMessage(INSTANCE, resource, "contributors[1]", CANNOT_BE_BLANK);
    }

    @Test
    public void publisherNull() {
        resource.setPublishers(Lists.newArrayList("onePublisher", null));
        assertValidatorMessage(INSTANCE, resource, "publishers[1]", CANNOT_BE_BLANK);
    }

    @Test
    public void publisherBlank() {
        resource.setPublishers(Lists.newArrayList("onePublisher", "  "));
        assertValidatorMessage(INSTANCE, resource, "publishers[1]", CANNOT_BE_BLANK);
    }
    
    @Test
    public void minHigherThanMax() {
        resource.setMinRevision(3);
        resource.setMaxRevision(2);
        assertValidatorMessage(INSTANCE, resource, "minRevision", MIN_OVER_MAX_ERROR);
    }
    
    @Test
    public void releaseNotesSpecifyOneVersionMinNull() {
        resource.setCategory(RELEASE_NOTE);
        resource.setMinRevision(null);
        resource.setMaxRevision(1);
        assertValidatorMessage(INSTANCE, resource, "category", RELEASE_NOTE_REVISION_ERROR);
    }

    @Test
    public void releaseNotesSpecifyOneVersionMaxNull() {
        resource.setCategory(RELEASE_NOTE);
        resource.setMinRevision(1);
        resource.setMaxRevision(null);
        assertValidatorMessage(INSTANCE, resource, "category", RELEASE_NOTE_REVISION_ERROR);
    }

    @Test
    public void releaseNotesSpecifyOneVersion() {
        resource.setCategory(RELEASE_NOTE);
        resource.setMinRevision(1);
        resource.setMaxRevision(2);
        assertValidatorMessage(INSTANCE, resource, "category", RELEASE_NOTE_REVISION_ERROR);
    }
}
