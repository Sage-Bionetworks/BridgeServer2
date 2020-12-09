package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.studies.Study;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class StudyValidator implements Validator {
    public static final StudyValidator INSTANCE = new StudyValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return Study.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Study study = (Study)object;
        
        if (isBlank(study.getIdentifier())) {
            errors.rejectValue("id", "is required");
        } else if (!study.getIdentifier().matches(BridgeConstants.BRIDGE_EVENT_ID_PATTERN)) {
            errors.rejectValue("id", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
        }
        if (isBlank(study.getAppId())) {
            errors.rejectValue("appId", "is required");
        }
        if (isBlank(study.getName())) {
            errors.rejectValue("name", "is required");
        }
    }
}
