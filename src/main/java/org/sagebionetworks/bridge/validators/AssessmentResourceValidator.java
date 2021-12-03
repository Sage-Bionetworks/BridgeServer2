package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;

public class AssessmentResourceValidator implements Validator {
    public static final AssessmentResourceValidator INSTANCE = new AssessmentResourceValidator();
    
    static final String RELEASE_NOTE_REVISION_ERROR = "must specify same min/max revision value for release notes";
    static final String MIN_OVER_MAX_ERROR = "should not be greater than maxRevision";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AssessmentResource.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        AssessmentResource resource = (AssessmentResource)target;

        if (isBlank(resource.getTitle())) {
            errors.rejectValue("title", CANNOT_BE_BLANK);
        }
        validateStringLength(errors, 255, resource.getTitle(), "title");
        if (isBlank(resource.getUrl())) {
            errors.rejectValue("url", CANNOT_BE_BLANK);
        }
        validateStringLength(errors, TEXT_SIZE, resource.getUrl(), "url");
        if (resource.getCategory() == null) {
            errors.rejectValue("category", CANNOT_BE_NULL);
        }
        if (resource.getCreators() != null) {
            for (int i=0; i < resource.getCreators().size(); i++) {
                String creator = resource.getCreators().get(i);
                if (isBlank(creator)) {
                    errors.rejectValue("creators["+i+"]", CANNOT_BE_BLANK);
                }
            }
            validateStringLength(errors, TEXT_SIZE, resource.getCreators().toString(), "creators");
        }
        if (resource.getContributors() != null) {
            for (int i=0; i < resource.getContributors().size(); i++) {
                String contrib = resource.getContributors().get(i);
                if (isBlank(contrib)) {
                    errors.rejectValue("contributors["+i+"]", CANNOT_BE_BLANK);
                }
            }
            validateStringLength(errors, TEXT_SIZE, resource.getContributors().toString(), "contributors");
        }
        if (resource.getPublishers() != null) {
            for (int i=0; i < resource.getPublishers().size(); i++) {
                String pub = resource.getPublishers().get(i);
                if (isBlank(pub)) {
                    errors.rejectValue("publishers["+i+"]", CANNOT_BE_BLANK);
                }
            }
            validateStringLength(errors, TEXT_SIZE, resource.getPublishers().toString(), "publishers");
        }
        if (resource.getMinRevision() != null && resource.getMaxRevision() != null && 
                resource.getMinRevision() > resource.getMaxRevision()) {
            errors.rejectValue("minRevision", MIN_OVER_MAX_ERROR);
        }
        if (resource.getCategory() == ResourceCategory.RELEASE_NOTE) {
            // findbugs doesn't like comparison operator with Integer, convert it
            if (resource.getMinRevision() == null || resource.getMaxRevision() == null ||
                    resource.getMinRevision().intValue() != resource.getMaxRevision().intValue()) {
                errors.rejectValue("category", RELEASE_NOTE_REVISION_ERROR);
            }
        }
        validateStringLength(errors, TEXT_SIZE, resource.getDescription(), "description");
        validateStringLength(errors, 255, resource.getLanguage(), "language");
        validateStringLength(errors, 255, resource.getFormat(), "format");
        validateStringLength(errors, 255, resource.getDate(), "date");
    }
}
