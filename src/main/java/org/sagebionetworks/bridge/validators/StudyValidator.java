package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.OWASP_REGEXP_VALID_EMAIL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyContact;

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
        // Requirements for contacts are very minimal. Every study tends to vary a bit.
        for (int i=0; i < study.getContacts().size(); i++) {
            StudyContact contact = study.getContacts().get(i);
            
            errors.pushNestedPath("contacts[" + i + "]");
            String email = contact.getEmail();
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue("email", "does not appear to be an email address");
            }
            Phone phone = contact.getPhone();
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
            if (contact.getRole() == null) {
                errors.rejectValue("role", CANNOT_BE_NULL);
            }
            if (StringUtils.isBlank(contact.getName())) {
                errors.rejectValue("name", CANNOT_BE_BLANK);
            }
            errors.popNestedPath();
            
        }
    }
}
