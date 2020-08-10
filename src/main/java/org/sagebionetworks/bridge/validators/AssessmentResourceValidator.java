package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;

public class AssessmentResourceValidator implements Validator {
    
    public static final AssessmentResourceValidator INSTANCE = new AssessmentResourceValidator();
    
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
        if (isBlank(resource.getUrl())) {
            errors.rejectValue("url", CANNOT_BE_BLANK);
        }
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
        }
        if (resource.getContributors() != null) {
            for (int i=0; i < resource.getContributors().size(); i++) {
                String contrib = resource.getContributors().get(i);
                if (isBlank(contrib)) {
                    errors.rejectValue("contributors["+i+"]", CANNOT_BE_BLANK);
                }
            }
        }
        if (resource.getPublishers() != null) {
            for (int i=0; i < resource.getPublishers().size(); i++) {
                String pub = resource.getPublishers().get(i);
                if (isBlank(pub)) {
                    errors.rejectValue("publishers["+i+"]", CANNOT_BE_BLANK);
                }
            }
        }
        if (resource.getMinRevision() != null && resource.getMaxRevision() != null && 
                resource.getMinRevision() > resource.getMaxRevision()) {
            errors.rejectValue("minRevision", "should not be greater than maxRevision");
        }
        if (resource.getCategory() == ResourceCategory.RELEASE_NOTE && 
                resource.getMinRevision() != null && 
                resource.getMinRevision() != resource.getMaxRevision()) {
            errors.rejectValue("category", "must specify same min/max revision value for release notes");
        }
    }
}
