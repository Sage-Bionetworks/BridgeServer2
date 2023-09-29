package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.validators.Validate.*;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateColorScheme;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateJsonLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.Map;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.services.OrganizationService;

public class AssessmentValidator implements Validator {

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
        validateStringLength(errors, 255, assessment.getTitle(), "title");
        if (assessment.getPhase() == null) {
            errors.rejectValue("phase", CANNOT_BE_NULL);
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
        validateStringLength(errors, 255, assessment.getIdentifier(), "identifier");
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
        validateJsonLength(errors, TEXT_SIZE, assessment.getCustomizationFields(), "customizationFields");
        validateColorScheme(errors, assessment.getColorScheme(), "colorScheme");
        if (!assessment.getLabels().isEmpty()) {
            validateLabels(errors, assessment.getLabels());
        }
        validateJsonLength(errors, TEXT_SIZE, assessment.getLabels(), "labels");
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
            if (!organizationService.getOrganizationOpt(ownerAppId, ownerOrgId).isPresent()) {
                errors.rejectValue("ownerId", "is not a valid organization ID");
            }
        }
        if (assessment.getMinutesToComplete() != null && assessment.getMinutesToComplete() < 0) {
            errors.rejectValue("minutesToComplete", CANNOT_BE_NEGATIVE);
        }
        if (assessment.getTags() != null) {
            for (String tag : assessment.getTags()) {
                validateStringLength(errors, 255, tag, "tags["+tag+"]");
            }
        }
        validateStringLength(errors, TEXT_SIZE, assessment.getSummary(), "summary");
        validateStringLength(errors, TEXT_SIZE, assessment.getValidationStatus(), "validationStatus");
        validateStringLength(errors, TEXT_SIZE, assessment.getNormingStatus(), "normingStatus");
        if (assessment.getImageResource() != null) {
            errors.pushNestedPath("imageResource");
            Validate.entity(ImageResourceValidator.INSTANCE, errors, assessment.getImageResource());
            errors.popNestedPath();
        }
        validateStringLength(errors, 255, assessment.getFrameworkIdentifier(), "frameworkIdentifier");
        validateStringLength(errors, 500, assessment.getJsonSchemaUrl(), "jsonSchemaUrl");
        validateStringLength(errors, 255, assessment.getCategory(), "category");
        if (assessment.getMinAge() != null && assessment.getMinAge() < 0) {
            errors.rejectValue("minAge", "cannot be less than 0");
        }
        if (assessment.getMaxAge() != null && assessment.getMaxAge() < 0) {
            errors.rejectValue("maxAge", "cannot be less than 0");
        }
        if (assessment.getMinAge() != null && assessment.getMaxAge() != null
                && assessment.getMinAge() > assessment.getMaxAge()) {
            errors.rejectValue("minAge and maxAge", "minAge cannot be larger than maxAge");
        }
        if (assessment.getAdditionalMetadata() != null && !assessment.getAdditionalMetadata().isObject()) {
            errors.rejectValue("additionalMetadata", INVALID_TYPE);
        }
        validateJsonLength(errors, TEXT_SIZE, assessment.getAdditionalMetadata(), "additionalMetadata");
    }
}
