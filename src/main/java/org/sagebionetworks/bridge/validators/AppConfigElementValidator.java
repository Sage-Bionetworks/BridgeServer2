package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.APP_CONFIG_ELEMENT_ID_PATTERN;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;

import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class AppConfigElementValidator implements Validator {

    public static final AppConfigElementValidator INSTANCE = new AppConfigElementValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return AppConfigElement.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        AppConfigElement appConfigElement = (AppConfigElement)object;
        
        if (isBlank(appConfigElement.getId())) {
            errors.rejectValue("id", "is required");
        } else if (!appConfigElement.getId().matches(APP_CONFIG_ELEMENT_ID_PATTERN)) {
            errors.rejectValue("id", BRIDGE_EVENT_ID_ERROR);
        }
        if (appConfigElement.getRevision() == null) {
            errors.rejectValue("revision", "is required");
        } else if (appConfigElement.getRevision() < 1) {
            errors.rejectValue("revision", "must be positive");
        }
        if (appConfigElement.getData() == null) {
            errors.rejectValue("data", "is required");
        }
    }
}
