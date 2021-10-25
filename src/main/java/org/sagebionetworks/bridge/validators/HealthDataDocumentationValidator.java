package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.nio.charset.StandardCharsets;

/** Validator for {@link org.sagebionetworks.bridge.models.HealthDataDocumentation}. */
public class HealthDataDocumentationValidator implements Validator {
    private static final Long MAX_DOCUMENTATION_BYTES = 1024L * 100;

    /** Singleton instance of this validator. */
    public static final HealthDataDocumentationValidator INSTANCE = new HealthDataDocumentationValidator();

    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return HealthDataDocumentation.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        if (object == null) {
            errors.rejectValue("HealthDataDocumentation", Validate.CANNOT_BE_NULL);
        } else if (!(object instanceof HealthDataDocumentation)) {
            errors.rejectValue("HealthDataDocumentation", Validate.WRONG_TYPE);
        } else {
            HealthDataDocumentation doc = (HealthDataDocumentation) object;

            // title
            String title = doc.getTitle();
            if (title == null) {
                errors.rejectValue("title", Validate.CANNOT_BE_NULL);
            } else if (StringUtils.isBlank(title)) {
                errors.rejectValue("title", Validate.CANNOT_BE_EMPTY_STRING);
            }

            // parent id
            String parentId = doc.getParentId();
            if (parentId == null) {
                errors.rejectValue("parentId", Validate.CANNOT_BE_NULL);
            } else if (StringUtils.isBlank(parentId)) {
                errors.rejectValue("parentId", Validate.CANNOT_BE_EMPTY_STRING);
            }

            // identifier
            String identifier = doc.getIdentifier();
            if (identifier == null) {
                errors.rejectValue("identifier", Validate.CANNOT_BE_NULL);
            } else if (StringUtils.isBlank(identifier)) {
                errors.rejectValue("identifier", Validate.CANNOT_BE_EMPTY_STRING);
            }

            // documentation
            String documentation = doc.getDocumentation();
            if (documentation == null) {
                errors.rejectValue("documentation", Validate.CANNOT_BE_NULL);
            } else if (StringUtils.isBlank(documentation)) {
                errors.rejectValue("documentation", Validate.CANNOT_BE_EMPTY_STRING);
            } else if (documentation.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENTATION_BYTES) {
                errors.rejectValue("documentation", Validate.EXCEEDS_MAXIMUM_SIZE);
            }
        }
    }
}
