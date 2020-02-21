package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_PATTERN;

import java.util.Optional;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.assessments.Assessment;

public class AssessmentValidator implements Validator {

    static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    private final Optional<Assessment> optional;
    
    public AssessmentValidator(Optional<Assessment> optional) {
        this.optional = optional;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Assessment.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Assessment assessment = (Assessment)target;
        
        if (isBlank(assessment.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);   
        }
        if (isBlank(assessment.getTitle())) {
            errors.rejectValue("title", CANNOT_BE_BLANK);   
        }
        if (isBlank(assessment.getOsName())) {
            errors.rejectValue("osName", CANNOT_BE_BLANK);   
        } else if (!OperatingSystem.ALL_OS_SYSTEMS.contains(assessment.getOsName())) {
            errors.rejectValue("osName", "is not a supported platform");
        }
        if (isBlank(assessment.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        } else if (optional.isPresent()) {
            errors.rejectValue("identifier", "already exists in revision " + assessment.getRevision());
        } else if (!assessment.getIdentifier().matches(BRIDGE_EVENT_ID_PATTERN)) {
            errors.rejectValue("identifier", BRIDGE_EVENT_ID_ERROR);
        }
        if (assessment.getRevision() < 0) {
            errors.rejectValue("revision", "cannot be negative");   
        }
        // ownerId == substudyId except in the shared assessments study, where it must include
        // the study as a namespace prefix, e.g. "studyId:substudyId". Assessments are always 
        // owned by some organization.
        if (isBlank(assessment.getOwnerId())) {
            errors.rejectValue("ownerId", CANNOT_BE_BLANK);
        }
    }
}
