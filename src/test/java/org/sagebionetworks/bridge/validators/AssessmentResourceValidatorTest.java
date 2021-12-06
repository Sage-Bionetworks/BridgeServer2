package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.models.assessments.ResourceCategory.RELEASE_NOTE;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.MIN_OVER_MAX_ERROR;
import static org.sagebionetworks.bridge.validators.AssessmentResourceValidator.RELEASE_NOTE_REVISION_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;

import java.util.Arrays;

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
    public void creatorTooMany() {
        resource.setCreators(Arrays.asList(new String[201]));
        assertValidatorMessage(INSTANCE, resource, "creators", "cannot have more than 50 list items");
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
    public void contributorTooMany() {
        resource.setContributors(Arrays.asList(new String[201]));
        assertValidatorMessage(INSTANCE, resource, "contributors", "cannot have more than 50 list items");
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
    public void publisherTooMany() {
        resource.setPublishers(Arrays.asList(new String[201]));
        assertValidatorMessage(INSTANCE, resource, "publishers", "cannot have more than 50 list items");
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
    
    @Test
    public void stringLengthValidation_title() {
        resource.setTitle(generateStringOfLength(256));
        assertValidatorMessage(INSTANCE, resource, "title", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_url() {
        resource.setUrl(generateStringOfLength(TEXT_SIZE + 1));
        assertValidatorMessage(INSTANCE, resource, "url", getInvalidStringLengthMessage(TEXT_SIZE));
    }
    
    @Test
    public void stringLengthValidation_description() {
        resource.setDescription(generateStringOfLength(TEXT_SIZE + 1));
        assertValidatorMessage(INSTANCE, resource, "description", getInvalidStringLengthMessage(TEXT_SIZE));
    }
    
    @Test
    public void stringLengthValidation_language() {
        resource.setLanguage(generateStringOfLength(256));
        assertValidatorMessage(INSTANCE, resource, "language", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_format() {
        resource.setFormat(generateStringOfLength(256));
        assertValidatorMessage(INSTANCE, resource, "format", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_date() {
        resource.setDate(generateStringOfLength(256));
        assertValidatorMessage(INSTANCE, resource, "date", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contributors() {
        resource.setContributors(ImmutableList.of(generateStringOfLength(256)));
        assertValidatorMessage(INSTANCE, resource, "contributors[0]", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_creators() {
        resource.setCreators(ImmutableList.of(generateStringOfLength(256)));
        assertValidatorMessage(INSTANCE, resource, "creators[0]", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_publishers() {
        resource.setPublishers(ImmutableList.of(generateStringOfLength(256)));
        assertValidatorMessage(INSTANCE, resource, "publishers[0]", getInvalidStringLengthMessage(255));
    }
}
