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
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.HashSet;
import java.util.Set;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Address;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class StudyValidator implements Validator {
    
    public static final StudyValidator INSTANCE = new StudyValidator();
    
    static final String ADDRESS_FIELD = "address";
    static final String AFFILIATION_FIELD = "affiliation";
    static final String APP_ID_FIELD = "appId";
    static final String CITY_FIELD = "city";
    static final String CONTACTS_FIELD = "contacts";
    static final String COUNTRY_FIELD = "country";
    static final String CUSTOM_EVENTS_FIELD = "customEvents";
    static final String DIVISION_FIELD = "division";
    static final String EMAIL_FIELD = "email";
    static final String IDENTIFIER_FIELD = "identifier";
    static final String IRB_DECISION_ON_FIELD = "irbDecisionOn";
    static final String IRB_DECISION_TYPE_FIELD = "irbDecisionType";
    static final String IRB_EXPIRES_ON_FIELD = "irbExpiresOn";
    static final String JURISDICTION_FIELD = "jurisdiction";
    static final String NAME_FIELD = "name";
    static final String MAIL_ROUTING_FIELD = "mailRouting";
    static final String PHASE_FIELD = "phase";
    static final String PHONE_FIELD = "phone";
    static final String PLACE_NAME_FIELD = "placeName";
    static final String POSITION_FIELD = "position";
    static final String POSTAL_CODE_FIELD = "postalCode";
    static final String ROLE_FIELD = "role";
    static final String STATE_FIELD = "state";
    static final String STREET_FIELD = "street";

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
        // TODO: is this the right field name? should it be id?
        validateStringLength(errors, 60, study.getIdentifier(), "studyId");
        if (isBlank(study.getAppId())) {
            errors.rejectValue(APP_ID_FIELD, CANNOT_BE_BLANK);
        }
        if (isBlank(study.getName())) {
            errors.rejectValue(NAME_FIELD, CANNOT_BE_BLANK);
        }
        validateStringLength(errors, 255, study.getName(), NAME_FIELD);
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
        Set<String> uniqueIds = new HashSet<>();
        for (int i=0; i < study.getCustomEvents().size(); i++) {
            StudyCustomEvent customEvent = study.getCustomEvents().get(i);
            
            errors.pushNestedPath(CUSTOM_EVENTS_FIELD + "["+i+"]");
            if (customEvent == null) {
                errors.rejectValue("", Validate.CANNOT_BE_NULL);
                continue;
            }
            if (isBlank(customEvent.getEventId())) {
                errors.rejectValue("eventId", CANNOT_BE_BLANK);
            } else if (!customEvent.getEventId().matches(BRIDGE_EVENT_ID_PATTERN)) {
                errors.rejectValue("eventId", BRIDGE_EVENT_ID_ERROR);
            } else {
                uniqueIds.add(customEvent.getEventId());    
            }
            if (customEvent.getUpdateType() == null) {
                errors.rejectValue("updateType", CANNOT_BE_NULL);
            }
            errors.popNestedPath();
        }
        if (uniqueIds.size() > 0 && (uniqueIds.size() != study.getCustomEvents().size())) {
            errors.rejectValue(CUSTOM_EVENTS_FIELD, "cannot contain duplidate event IDs");
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
            validateStringLength(errors, 255, contact.getName(), NAME_FIELD);
            String email = contact.getEmail();
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue(EMAIL_FIELD, INVALID_EMAIL_ERROR);
            }
            validateStringLength(errors, 255, contact.getEmail(), EMAIL_FIELD);
            Phone phone = contact.getPhone();
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue(PHONE_FIELD, INVALID_PHONE_ERROR);
            }
            validateStringLength(errors, 255, contact.getPosition(), POSITION_FIELD);
            validateStringLength(errors, 255, contact.getAffiliation(), AFFILIATION_FIELD);
            validateStringLength(errors, 255, contact.getJurisdiction(), JURISDICTION_FIELD);
            if (contact.getAddress() != null) {
                Address address = contact.getAddress();
                errors.pushNestedPath(ADDRESS_FIELD);
                validateStringLength(errors, 255, address.getPlaceName(), PLACE_NAME_FIELD);
                validateStringLength(errors, 255, address.getStreet(), STREET_FIELD);
                validateStringLength(errors, 255, address.getDivision(), DIVISION_FIELD);
                validateStringLength(errors, 255, address.getMailRouting(), MAIL_ROUTING_FIELD);
                validateStringLength(errors, 255, address.getCity(), CITY_FIELD);
                validateStringLength(errors, 50, address.getPostalCode(), POSTAL_CODE_FIELD);
                validateStringLength(errors, 255, address.getCountry(), COUNTRY_FIELD);
                errors.popNestedPath();
            }
            errors.popNestedPath();
        }
        // TODO: This looks like it is not nullable. Maybe add validation for that.
        if (study.getStudyDesignTypes() != null) {
            for (String designType : study.getStudyDesignTypes()) {
                validateStringLength(errors, 255, designType, "studyDesignType");
            }
        }
        if (study.getDiseases() != null) {
            for (String disease : study.getDiseases()) {
                validateStringLength(errors, 255, disease, "disease");
            }
        }
        if (study.getClientData() != null) {
            validateStringLength(errors, TEXT_SIZE, study.getClientData().toString(), "clientData");
        }
        validateStringLength(errors, 510, study.getDetails(), "details");
        validateStringLength(errors, 255, study.getStudyLogoUrl(), "studyLogoUrl");
        validateStringLength(errors, 255, study.getInstitutionId(), "institutionId");
        validateStringLength(errors, 255, study.getIrbProtocolId(), "irbProtocolId");
        validateStringLength(errors, 512, study.getIrbProtocolName(), "irbProtocolName");
        validateStringLength(errors, 60, study.getIrbName(), "irbName");
        validateStringLength(errors, 255, study.getKeywords(), "keywords");
    }
}
