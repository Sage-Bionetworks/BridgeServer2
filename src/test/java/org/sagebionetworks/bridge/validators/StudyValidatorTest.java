package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.models.studies.ContactRole.TECHNICAL_SUPPORT;
import static org.sagebionetworks.bridge.models.studies.IrbDecisionType.APPROVED;
import static org.sagebionetworks.bridge.models.studies.IrbDecisionType.EXEMPT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.validators.StudyValidator.APP_ID_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.CONTACTS_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.CUSTOM_EVENTS_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.DISEASES_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.EMAIL_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IDENTIFIER_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IRB_DECISION_ON_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IRB_DECISION_TYPE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IRB_EXPIRES_ON_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.NAME_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.PHASE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.PHONE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.ROLE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.STUDY_DESIGN_TYPES_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.entityThrowingException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnum;
import org.sagebionetworks.bridge.models.appconfig.AppConfigEnumEntry;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

public class StudyValidatorTest {
    
    private static final LocalDate DECISION_ON = DateTime.now().toLocalDate();
    private static final LocalDate EXPIRES_ON = DateTime.now().plusDays(10).toLocalDate();
    
    private StudyValidator validator;
    private Study study;
    
    @BeforeMethod
    public void beforeMethod() {
        AppConfigEnum diseases = new AppConfigEnum();
        AppConfigEnum resTypes = new AppConfigEnum();
        validator = new StudyValidator(diseases, resTypes);
    }
    
    @Test
    public void valid() {
        study = createStudy();
        entityThrowingException(validator, study);
    }
    
    @Test
    public void idIsRequired() {
        study = createStudy();
        study.setIdentifier(null);
        assertValidatorMessage(validator, study, IDENTIFIER_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void invalidIdentifier() {
        study = createStudy();
        study.setIdentifier("id not valid");
        
        assertValidatorMessage(validator, study, IDENTIFIER_FIELD, BRIDGE_EVENT_ID_ERROR);
    }

    @Test
    public void nameIsRequired() {
        study = createStudy();
        study.setName(null);
        assertValidatorMessage(validator, study, NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void phaseRequired() {
        study = createStudy();
        study.setPhase(null);
        assertValidatorMessage(validator, study, PHASE_FIELD, CANNOT_BE_NULL);
    }

    @Test
    public void appIdIsRequired() {
        study = createStudy();
        study.setAppId(null);
        assertValidatorMessage(validator, study, APP_ID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactNameNull() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setName(null);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactNameBlank() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setName("");
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactRoleNull() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setRole(null);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + ROLE_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void contactInvalidEmail() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setEmail("junk");
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + EMAIL_FIELD, INVALID_EMAIL_ERROR);
    }
    
    @Test
    public void contactInvalidPhone() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setPhone(new Phone("333333", "Portual"));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + PHONE_FIELD, INVALID_PHONE_ERROR);
    }
    
    @Test
    public void irbDecisionOnRequired() {
        study = createStudy();
        study.setIrbDecisionType(APPROVED);
        study.setIrbExpiresOn(EXPIRES_ON);
        
        assertValidatorMessage(validator, study, IRB_DECISION_ON_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void irbDecisionTypeRequired() {
        study = createStudy();
        study.setIrbDecisionOn(DECISION_ON);
        study.setIrbExpiresOn(EXPIRES_ON);
        
        assertValidatorMessage(validator, study, IRB_DECISION_TYPE_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void irbExpiresOnRequired() {
        study = createStudy();
        study.setIrbDecisionOn(DECISION_ON);
        study.setIrbDecisionType(APPROVED);
        
        assertValidatorMessage(validator, study, IRB_EXPIRES_ON_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void irbExemptionDoesNotRequireExpiration() {
        study = createStudy();
        study.setIrbDecisionType(EXEMPT);
        study.setIrbDecisionOn(DECISION_ON);
        study.setIrbExpiresOn(null);
        entityThrowingException(validator, study);
    }
    
    @Test
    public void nullContactsOK() {
        study = createStudy();
        study.setContacts(null);
        
        entityThrowingException(validator, study);
    }
    
    @Test
    public void nullContactEmailOK() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setEmail(null);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(validator, study);
    }

    @Test
    public void nullContactPhoneOK() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setPhone(null);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(validator, study);
    }
    
    @Test
    public void customEvents_eventIdBank() {
        StudyCustomEvent event = new StudyCustomEvent("", MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0].eventId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void customEvents_eventIdNull() {
        StudyCustomEvent event = new StudyCustomEvent(null, MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0].eventId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void customEvents_eventIdInvalid() {
        StudyCustomEvent event = new StudyCustomEvent("a b c", MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0].eventId", BRIDGE_EVENT_ID_ERROR);
    }
    
    @Test
    public void customEvents_eventIdReservedKeyword() {
        StudyCustomEvent event = new StudyCustomEvent("Timeline_Retrieved", MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0].eventId", "is a reserved system event ID");
    }
    
    @Test
    public void customEvents_updateTypeNull() {
        StudyCustomEvent event = new StudyCustomEvent("event", null);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0].updateType", CANNOT_BE_NULL);
    }
    
    @Test
    public void customEvents_entryNull() {
        study = createStudy();
        study.getCustomEvents().add(null);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0]", CANNOT_BE_NULL);
    }
    
    @Test
    public void customEvents_duplicated() {
        StudyCustomEvent event = new StudyCustomEvent("event", FUTURE_ONLY);
        study = createStudy();
        study.getCustomEvents().add(event);
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD, "cannot contain duplidate event IDs");
    }

    @Test
    public void diseases_invalidValue() {
        validator = new StudyValidator(createDiseasesEnum(), new AppConfigEnum());
        
        study = createStudy();
        study.setDiseases(ImmutableSet.of("Wrist pain"));

        assertValidatorMessage(validator, study, DISEASES_FIELD, "“Wrist pain” is not a recognized disease");
    }
    
    @Test
    public void diseases_skipValidation() {
        AppConfigEnum configEnum = createDiseasesEnum();
        configEnum.setValidate(false);
        validator = new StudyValidator(configEnum, new AppConfigEnum());
        
        study = createStudy();
        study.setDiseases(ImmutableSet.of("Wrist pain"));

        entityThrowingException(validator, study);
    }
    
    @Test
    public void studyDesignTypes_invalidValue() {
        validator = new StudyValidator(new AppConfigEnum(), createStudyDesignsEnum());
        
        study = createStudy();
        study.setStudyDesignTypes(ImmutableSet.of("Cool"));

        assertValidatorMessage(validator, study, STUDY_DESIGN_TYPES_FIELD, "“Cool” is not a recognized study design type");
    }
    
    @Test
    public void studyDesignTypes_skipValidation() {
        AppConfigEnum configEnum = createStudyDesignsEnum();
        configEnum.setValidate(false);
        validator = new StudyValidator(new AppConfigEnum(), configEnum);
        
        study = createStudy();
        study.setStudyDesignTypes(ImmutableSet.of("Cool"));

        entityThrowingException(validator, study);
    }
    
    private Study createStudy() {
        Study study = Study.create();
        study.setIdentifier("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        study.setPhase(DESIGN);
        return study;
    }
    
    private Contact createContact() {
        Contact contact = new Contact();
        contact.setName("Tim Powers");
        contact.setRole(TECHNICAL_SUPPORT);
        contact.setEmail(EMAIL);
        contact.setPhone(PHONE);
        return contact;
    }
    
    private AppConfigEnum createDiseasesEnum() {
        AppConfigEnumEntry e1 = new AppConfigEnumEntry();
        e1.setValue("Growing pains");
        
        AppConfigEnumEntry e2 = new AppConfigEnumEntry();
        e2.setValue("Yips");
        
        AppConfigEnum configEnum = new AppConfigEnum();
        configEnum.setValidate(true);
        configEnum.setEntries(ImmutableList.of(e1, e2));
        return configEnum;
    }

    private AppConfigEnum createStudyDesignsEnum() {
        AppConfigEnumEntry e1 = new AppConfigEnumEntry();
        e1.setValue("Observational");
        
        AppConfigEnumEntry e2 = new AppConfigEnumEntry();
        e2.setValue("Experimental");
        
        AppConfigEnum configEnum = new AppConfigEnum();
        configEnum.setValidate(true);
        configEnum.setEntries(ImmutableList.of(e1, e2));
        return configEnum;
    }
}
