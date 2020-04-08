package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_PATTERN;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import java.util.Map;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SubstudyService;

public class AssessmentValidator implements Validator {

    private final SubstudyService substudyService;
    private final String appId;
    
    public AssessmentValidator(SubstudyService substudyService, String appId) {
        this.substudyService = substudyService;
        this.appId = appId;
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
        String osName = assessment.getOsName();
        if (isBlank(assessment.getOsName())) {
            errors.rejectValue("osName", CANNOT_BE_BLANK);   
        } else if (!OperatingSystem.ALL_OS_SUPPORT_OPTIONS.contains(osName)) {
            errors.rejectValue("osName", "is not a supported platform");
        }
        if (isBlank(assessment.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        } else if (!assessment.getIdentifier().matches(BRIDGE_EVENT_ID_PATTERN)) {
            errors.rejectValue("identifier", BRIDGE_EVENT_ID_ERROR);
        }
        if (assessment.getRevision() < 0) {
            errors.rejectValue("revision", "cannot be negative");   
        }
        if (assessment.getCustomizationFields() != null && !assessment.getCustomizationFields().isEmpty()) {
            for (Map.Entry<String, Set<PropertyInfo>> entry : assessment.getCustomizationFields().entrySet()) {
                String key = entry.getKey();
                
                int i=0;
                for (PropertyInfo info : entry.getValue()) {
                    errors.pushNestedPath("customizationFields["+key+"][" + i + "]");
                    if (isBlank(info.getPropName())) {
                        errors.rejectValue("propName", CANNOT_BE_BLANK);    
                    }
                    if (isBlank(info.getLabel())) {
                        errors.rejectValue("label", CANNOT_BE_BLANK);
                    }
                    errors.popNestedPath();
                    i++;
                }
            }
        }
        
        // ownerId == substudyId except in the shared assessments study, where it must include
        // the study as a namespace prefix, e.g. "studyId:substudyId". Assessments are always 
        // owned by some organization.
        if (isBlank(assessment.getOwnerId())) {
            errors.rejectValue("ownerId", CANNOT_BE_BLANK);
        } else {
            StudyIdentifier app = null;
            String ownerId = null;
            if (SHARED_APP_ID.equals(appId)) {
                String[] parts = assessment.getOwnerId().split(":");
                app = new StudyIdentifierImpl(parts[0]);
                ownerId = parts[1];
            } else {
                app = new StudyIdentifierImpl(appId);
                ownerId = assessment.getOwnerId();
            }
            if (substudyService.getSubstudy(app, ownerId, false) == null) {
                errors.rejectValue("ownerId", "is not a valid organization ID");
            }
        }
    }
}
