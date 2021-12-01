package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_RELAXED_ID_ERROR;
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
import static org.sagebionetworks.bridge.validators.StudyValidator.ADHERENCE_THRESHOLD_PERCENTAGE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.APP_ID_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.CONTACTS_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.CUSTOM_EVENTS_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.EMAIL_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IDENTIFIER_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.StudyValidator.IRB_DECISION_ON_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IRB_DECISION_TYPE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.IRB_EXPIRES_ON_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.NAME_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.PHASE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.PHONE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.ROLE_FIELD;
import static org.sagebionetworks.bridge.validators.StudyValidator.STUDY_TIME_ZONE_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.TIME_ZONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.entityThrowingException;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

public class StudyValidatorTest {
    
    private static final LocalDate DECISION_ON = DateTime.now().toLocalDate();
    private static final LocalDate EXPIRES_ON = DateTime.now().plusDays(10).toLocalDate();
    
    private Study study;
    
    @Test
    public void valid() {
        study = createStudy();
        entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void idIsRequired() {
        study = createStudy();
        study.setIdentifier(null);
        assertValidatorMessage(INSTANCE, study, IDENTIFIER_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void invalidIdentifier() {
        study = createStudy();
        study.setIdentifier("id not valid");
        
        assertValidatorMessage(INSTANCE, study, IDENTIFIER_FIELD, BRIDGE_EVENT_ID_ERROR);
    }

    @Test
    public void nameIsRequired() {
        study = createStudy();
        study.setName(null);
        assertValidatorMessage(INSTANCE, study, NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void phaseRequired() {
        study = createStudy();
        study.setPhase(null);
        assertValidatorMessage(INSTANCE, study, PHASE_FIELD, CANNOT_BE_NULL);
    }

    @Test
    public void appIdIsRequired() {
        study = createStudy();
        study.setAppId(null);
        assertValidatorMessage(INSTANCE, study, APP_ID_FIELD, CANNOT_BE_BLANK);
    }

    @Test
    public void studyTimeZoneNullOK() {
        study = createStudy();
        study.setStudyTimeZone(null);
        entityThrowingException(INSTANCE, study);
    }

    @Test
    public void studyTimeZoneInvalid() {
        study = createStudy();
        study.setStudyTimeZone("America/Aspen");
        assertValidatorMessage(INSTANCE, study, STUDY_TIME_ZONE_FIELD, TIME_ZONE_ERROR);
    }
    
    @Test
    public void adherenceThresholdPercentageNullOK() {
        study = createStudy();
        study.setAdherenceThresholdPercentage(null);
        entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void adherenceThresholdPercentageLessThanZero() {
        study = createStudy();
        study.setAdherenceThresholdPercentage(-1);
        assertValidatorMessage(INSTANCE, study, ADHERENCE_THRESHOLD_PERCENTAGE_FIELD, "must be from 0-100%");
    }
    
    @Test
    public void adherenceThresholdPercentageMoreThan100() {
        study = createStudy();
        study.setAdherenceThresholdPercentage(101);
        assertValidatorMessage(INSTANCE, study, ADHERENCE_THRESHOLD_PERCENTAGE_FIELD, "must be from 0-100%");
    }

    @Test
    public void contactNameNull() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setName(null);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(INSTANCE, study, CONTACTS_FIELD + "[0]." + NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactNameBlank() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setName("");
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(INSTANCE, study, CONTACTS_FIELD + "[0]." + NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactRoleNull() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setRole(null);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(INSTANCE, study, CONTACTS_FIELD + "[0]." + ROLE_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void contactInvalidEmail() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setEmail("junk");
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(INSTANCE, study, CONTACTS_FIELD + "[0]." + EMAIL_FIELD, INVALID_EMAIL_ERROR);
    }
    
    @Test
    public void contactInvalidPhone() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setPhone(new Phone("333333", "Portual"));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(INSTANCE, study, CONTACTS_FIELD + "[0]." + PHONE_FIELD, INVALID_PHONE_ERROR);
    }
    
    @Test
    public void irbDecisionOnRequired() {
        study = createStudy();
        study.setIrbDecisionType(APPROVED);
        study.setIrbExpiresOn(EXPIRES_ON);
        
        assertValidatorMessage(INSTANCE, study, IRB_DECISION_ON_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void irbDecisionTypeRequired() {
        study = createStudy();
        study.setIrbDecisionOn(DECISION_ON);
        study.setIrbExpiresOn(EXPIRES_ON);
        
        assertValidatorMessage(INSTANCE, study, IRB_DECISION_TYPE_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void irbExpiresOnRequired() {
        study = createStudy();
        study.setIrbDecisionOn(DECISION_ON);
        study.setIrbDecisionType(APPROVED);
        
        assertValidatorMessage(INSTANCE, study, IRB_EXPIRES_ON_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void irbExemptionDoesNotRequireExpiration() {
        study = createStudy();
        study.setIrbDecisionType(EXEMPT);
        study.setIrbDecisionOn(DECISION_ON);
        study.setIrbExpiresOn(null);
        entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void nullContactsOK() {
        study = createStudy();
        study.setContacts(null);
        
        entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void nullContactEmailOK() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setEmail(null);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(INSTANCE, study);
    }

    @Test
    public void nullContactPhoneOK() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setPhone(null);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void customEvents_eventIdBank() {
        StudyCustomEvent event = new StudyCustomEvent("", MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(INSTANCE, study, CUSTOM_EVENTS_FIELD + "[0].eventId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void customEvents_eventIdNull() {
        StudyCustomEvent event = new StudyCustomEvent(null, MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(INSTANCE, study, CUSTOM_EVENTS_FIELD + "[0].eventId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void customEvents_eventIdInvalid() {
        StudyCustomEvent event = new StudyCustomEvent("a:b:c", MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(INSTANCE, study, CUSTOM_EVENTS_FIELD + "[0].eventId", BRIDGE_RELAXED_ID_ERROR);
    }
    
    @Test
    public void customEvents_updateTypeNull() {
        StudyCustomEvent event = new StudyCustomEvent("event", null);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(INSTANCE, study, CUSTOM_EVENTS_FIELD + "[0].updateType", CANNOT_BE_NULL);
    }
    
    @Test
    public void customEvents_entryNull() {
        study = createStudy();
        study.getCustomEvents().add(null);
        
        assertValidatorMessage(INSTANCE, study, CUSTOM_EVENTS_FIELD + "[0]", CANNOT_BE_NULL);
    }
    
    @Test
    public void customEvents_duplicated() {
        StudyCustomEvent event = new StudyCustomEvent("event", FUTURE_ONLY);
        study = createStudy();
        study.getCustomEvents().add(event);
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(INSTANCE, study, CUSTOM_EVENTS_FIELD, "cannot contain duplidate event IDs");
    }
    
    private Study createStudy() {
        Study study = Study.create();
        study.setIdentifier("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        study.setPhase(DESIGN);
        study.setStudyTimeZone("America/Los_Angeles");
        study.setAdherenceThresholdPercentage(80);
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
    
}
