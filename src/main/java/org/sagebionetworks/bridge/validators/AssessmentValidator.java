package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_PATTERN;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;

import java.util.Map;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.services.OrganizationService;

public class AssessmentValidator implements Validator {

    static final String INVALID_HEX_TRIPLET = "%s is not in hex triplet format (ie #FFF or #FFFFF format)";
    private static final String HEX_TRIPLET_FORMAT = "^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$";
    
    private final String appId;
    private final OrganizationService organizationService;
    
    public AssessmentValidator(String appId, OrganizationService organizationService) {
        this.appId = appId;
        this.organizationService = organizationService;
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
        if (!assessment.getCustomizationFields().isEmpty()) {
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
        if (assessment.getColorScheme() != null) {
            errors.pushNestedPath("colorScheme");
            ColorScheme cs = assessment.getColorScheme();
            if (cs.getBackground() != null && !cs.getBackground().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("background", INVALID_HEX_TRIPLET);
            }
            if (cs.getForeground() != null && !cs.getForeground().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("foreground", INVALID_HEX_TRIPLET);
            }
            if (cs.getActivated() != null && !cs.getActivated().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("activated", INVALID_HEX_TRIPLET);
            }
            if (cs.getInactivated() != null && !cs.getInactivated().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("inactivated", INVALID_HEX_TRIPLET);
            }
            errors.popNestedPath();
        }
        if (!assessment.getLabels().isEmpty()) {
            validateLabels(errors, assessment.getLabels());
        }
        
        // ownerId == studyId except in the shared assessments app, where it must include
        // the app as a namespace prefix, e.g. "appId:orgId". Assessments are always 
        // owned by some organization.
        if (isBlank(assessment.getOwnerId())) {
            errors.rejectValue("ownerId", CANNOT_BE_BLANK);
        } else {
            String ownerAppId = null;
            String ownerOrgId = null;
            if (SHARED_APP_ID.equals(appId)) {
                String[] parts = assessment.getOwnerId().split(":");
                ownerAppId = parts[0];
                ownerOrgId = parts[1];
            } else {
                ownerAppId = appId;
                ownerOrgId = assessment.getOwnerId();
            }
            if (organizationService.getOrganization(ownerAppId, ownerOrgId) == null) {
                errors.rejectValue("ownerId", "is not a valid organization ID");
            }
        }
        if (assessment.getMinutesToComplete() != null && assessment.getMinutesToComplete() < 0) {
            errors.rejectValue("minutesToComplete", CANNOT_BE_NEGATIVE);
        }
    }
}
