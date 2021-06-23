package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_PATTERN;
import static org.sagebionetworks.bridge.BridgeConstants.OWASP_REGEXP_VALID_EMAIL;
import static org.sagebionetworks.bridge.models.studies.IrbDecisionType.APPROVED;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class StudyValidator implements Validator {
    
    public static final StudyValidator INSTANCE = new StudyValidator();
    
    static final String APP_ID_FIELD = "appId";
    static final String CONTACTS_FIELD = "contacts";
    static final String EMAIL_FIELD = "email";
    static final String IDENTIFIER_FIELD = "identifier";
    static final String IRB_DECISION_ON_FIELD = "irbDecisionOn";
    static final String IRB_DECISION_TYPE_FIELD = "irbDecisionType";
    static final String IRB_EXPIRES_ON_FIELD = "irbExpiresOn";
    static final String NAME_FIELD = "name";
    static final String PHASE_FIELD = "phase";
    static final String PHONE_FIELD = "phone";
    static final String ROLE_FIELD = "role";

    @Override
    public boolean supports(Class<?> clazz) {
        return Study.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Study study = (Study)object;
        
        if (isBlank(study.getIdentifier())) {
            errors.rejectValue(IDENTIFIER_FIELD, CANNOT_BE_BLANK);
        } else if (!study.getIdentifier().matches(BRIDGE_EVENT_ID_PATTERN)) {
            errors.rejectValue(IDENTIFIER_FIELD, BRIDGE_EVENT_ID_ERROR);
        }
        if (isBlank(study.getAppId())) {
            errors.rejectValue(APP_ID_FIELD, CANNOT_BE_BLANK);
        }
        if (isBlank(study.getName())) {
            errors.rejectValue(NAME_FIELD, CANNOT_BE_BLANK);
        }
        if (study.getPhase() == null) {
            errors.rejectValue(PHASE_FIELD, CANNOT_BE_NULL);
        }
        // If one of these is supplied, all three need to be supplied
        boolean validateIrb = study.getIrbDecisionType() != null ||
                study.getIrbDecisionOn() != null ||
                study.getIrbExpiresOn() != null;
        if (validateIrb) {
            if (study.getIrbDecisionType() == null) {
                errors.rejectValue(IRB_DECISION_TYPE_FIELD, CANNOT_BE_NULL);
            } else if (study.getIrbDecisionType() == APPROVED && study.getIrbExpiresOn() == null) {
                errors.rejectValue(IRB_EXPIRES_ON_FIELD, CANNOT_BE_NULL);
            }                
            if (study.getIrbDecisionOn() == null) { 
                errors.rejectValue(IRB_DECISION_ON_FIELD, CANNOT_BE_NULL);
            }
        }
        for (int i=0; i < study.getContacts().size(); i++) {
            Contact contact = study.getContacts().get(i);
            errors.pushNestedPath(CONTACTS_FIELD + "[" + i + "]");
            if (contact.getRole() == null) {
                errors.rejectValue(ROLE_FIELD, CANNOT_BE_NULL);
            }
            if (isBlank(contact.getName())) {
                errors.rejectValue(NAME_FIELD, CANNOT_BE_BLANK);
            }
            String email = contact.getEmail();
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue(EMAIL_FIELD, INVALID_EMAIL_ERROR);
            }
            Phone phone = contact.getPhone();
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue(PHONE_FIELD, INVALID_PHONE_ERROR);
            }
            errors.popNestedPath();
        }
    }
}
