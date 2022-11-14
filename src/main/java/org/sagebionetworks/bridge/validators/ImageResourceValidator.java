package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.assessments.ImageResource;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ImageResourceValidator implements Validator {
    public static final ImageResourceValidator INSTANCE = new ImageResourceValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return ImageResource.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ImageResource imageResource = (ImageResource) target;

        if (StringUtils.isBlank(imageResource.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        validateStringLength(errors, 255, imageResource.getName(), "name");
        validateStringLength(errors, 255, imageResource.getModule(), "module");
        if (imageResource.getLabels() != null) {
            validateLabels(errors, imageResource.getLabels());
        }
    }
}
