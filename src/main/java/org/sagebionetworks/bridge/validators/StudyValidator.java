package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_PATTERN;
import static org.sagebionetworks.bridge.BridgeConstants.OWASP_REGEXP_VALID_EMAIL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Contact;
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
            errors.rejectValue("identifier", "is required");
        } else if (!study.getIdentifier().matches(BRIDGE_EVENT_ID_PATTERN)) {
            errors.rejectValue("identifier", BRIDGE_EVENT_ID_ERROR);
        }
        if (isBlank(study.getAppId())) {
            errors.rejectValue("appId", "is required");
        }
        if (isBlank(study.getName())) {
            errors.rejectValue("name", "is required");
        }
        if (study.getPhase() == null) {
            errors.rejectValue("phase", "is required");
        }
        for (int i=0; i < study.getContacts().size(); i++) {
            Contact contact = study.getContacts().get(i);
            errors.pushNestedPath("contacts[" + i + "]");
            if (contact.getRole() == null) {
                errors.rejectValue("role", CANNOT_BE_NULL);
            }
            if (isBlank(contact.getName())) {
                errors.rejectValue("name", CANNOT_BE_BLANK);
            }
            String email = contact.getEmail();
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue("email", "does not appear to be an email address");
            }
            Phone phone = contact.getPhone();
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
            errors.popNestedPath();
        }
    }
}
