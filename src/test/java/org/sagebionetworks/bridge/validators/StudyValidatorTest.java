package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getExcessivelyLargeClientData;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;

import java.util.Optional;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.models.studies.ContactRole.TECHNICAL_SUPPORT;
import static org.sagebionetworks.bridge.models.studies.IrbDecisionType.APPROVED;
import static org.sagebionetworks.bridge.models.studies.IrbDecisionType.EXEMPT;
import static org.sagebionetworks.bridge.models.studies.StudyPhase.DESIGN;
import static org.sagebionetworks.bridge.validators.StudyValidator.*;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_RELAXED_ID_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_TIME_ZONE;
import static org.sagebionetworks.bridge.validators.Validate.entityThrowingException;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

import com.google.common.collect.ImmutableList;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.models.studies.Address;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;
import org.sagebionetworks.bridge.services.Schedule2Service;
import org.sagebionetworks.bridge.services.SponsorService;

public class StudyValidatorTest extends Mockito {
    private static final LocalDate DECISION_ON = DateTime.now().toLocalDate();
    private static final LocalDate EXPIRES_ON = DateTime.now().plusDays(10).toLocalDate();
    
    private Study study;
    
    private StudyValidator validator;
    
    @Mock
    Schedule2Service mockScheduleService;

    @Mock
    SponsorService mockSponsorService;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        validator = new StudyValidator(Sets.newHashSet(), mockScheduleService, mockSponsorService);
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
    public void studyTimeZoneNullOK() {
        study = createStudy();
        study.setStudyTimeZone(null);
        entityThrowingException(validator, study);
    }

    @Test
    public void studyTimeZoneInvalid() {
        study = createStudy();
        study.setStudyTimeZone("America/Aspen");
        assertValidatorMessage(validator, study, STUDY_TIME_ZONE_FIELD, INVALID_TIME_ZONE);
    }
    
    @Test
    public void adherenceThresholdPercentageNullOK() {
        study = createStudy();
        study.setAdherenceThresholdPercentage(null);
        entityThrowingException(validator, study);
    }
    
    @Test
    public void adherenceThresholdPercentageLessThanZero() {
        study = createStudy();
        study.setAdherenceThresholdPercentage(-1);
        assertValidatorMessage(validator, study, ADHERENCE_THRESHOLD_PERCENTAGE_FIELD, "must be from 0-100%");
    }
    
    @Test
    public void adherenceThresholdPercentageMoreThan100() {
        study = createStudy();
        study.setAdherenceThresholdPercentage(101);
        assertValidatorMessage(validator, study, ADHERENCE_THRESHOLD_PERCENTAGE_FIELD, "must be from 0-100%");
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
        StudyCustomEvent event = new StudyCustomEvent("a:b:c", MUTABLE);
        study = createStudy();
        study.getCustomEvents().add(event);
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD + "[0].eventId", BRIDGE_RELAXED_ID_ERROR);
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
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD, "cannot contain duplicate event IDs");
    }
    
    @Test
    public void customEvents_missingEventIdInExistingSchedule() {
        study = createStudy();

        validator = new StudyValidator(Sets.newHashSet("aaa", "ccc"), mockScheduleService, mockSponsorService);
    
        StudyCustomEvent studyCustomEvent = new StudyCustomEvent();
        studyCustomEvent.setEventId("aaa");
        
        study.setCustomEvents(ImmutableList.of(studyCustomEvent));
        
        assertValidatorMessage(validator, study, CUSTOM_EVENTS_FIELD, "cannot remove custom events currently used in a schedule: ccc");
    }
    
    @Test
    public void customEvents_emptySetOk() {
        study = createStudy();

        validator = new StudyValidator(Sets.newHashSet(), mockScheduleService, mockSponsorService);
        
        StudyCustomEvent studyCustomEvent = new StudyCustomEvent();
        studyCustomEvent.setEventId("aaa");
        studyCustomEvent.setUpdateType(MUTABLE);
        
        study.setCustomEvents(ImmutableList.of(studyCustomEvent));
        
        Validate.entityThrowingException(validator, study);
    }
    
    @Test
    public void customEvents_nullSetOk() {
        study = createStudy();

        validator = new StudyValidator(null, mockScheduleService, mockSponsorService);
        
        StudyCustomEvent studyCustomEvent = new StudyCustomEvent();
        studyCustomEvent.setEventId("aaa");
        studyCustomEvent.setUpdateType(MUTABLE);
        
        study.setCustomEvents(ImmutableList.of(studyCustomEvent));
        
        Validate.entityThrowingException(validator, study);
    }
    
    @Test
    public void contactWithAddressOK() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(validator, study);
    }
    
    @Test
    public void stringLengthValidation_identifier() {
        study = createStudy();
        study.setIdentifier(generateStringOfLength(256));
        assertValidatorMessage(validator, study, IDENTIFIER_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_name() {
        study = createStudy();
        study.setName(generateStringOfLength(256));
        assertValidatorMessage(validator, study, NAME_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_details() {
        study = createStudy();
        study.setDetails(generateStringOfLength(511));
        assertValidatorMessage(validator, study, DETAILS_FIELD, getInvalidStringLengthMessage(510));
    }
    
    @Test
    public void stringLengthValidation_studyLogoUrl() {
        study = createStudy();
        study.setStudyLogoUrl(generateStringOfLength(256));
        assertValidatorMessage(validator, study, STUDY_LOGO_URL_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_institutionId() {
        study = createStudy();
        study.setInstitutionId(generateStringOfLength(256));
        assertValidatorMessage(validator, study, INSTITUTION_ID_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_irbProtocolId() {
        study = createStudy();
        study.setIrbProtocolId(generateStringOfLength(256));
        assertValidatorMessage(validator, study, IRB_PROTOCOL_ID_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_irbName() {
        study = createStudy();
        study.setIrbName(generateStringOfLength(61));
        assertValidatorMessage(validator, study, IRB_NAME_FIELD, getInvalidStringLengthMessage(60));
    }
    
    @Test
    public void stringLengthValidation_irbProtocolName() {
        study = createStudy();
        study.setIrbProtocolName(generateStringOfLength(513));
        assertValidatorMessage(validator, study, IRB_PROTOCOL_NAME_FIELD, getInvalidStringLengthMessage(512));
    }
    
    @Test
    public void stringLengthValidation_keywords() {
        study = createStudy();
        study.setKeywords(generateStringOfLength(256));
        assertValidatorMessage(validator, study, KEYWORDS_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactName() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setName(generateStringOfLength(256));
        study.setContacts(ImmutableList.of(c1));
    
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + NAME_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactPosition() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setPosition(generateStringOfLength(256));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + POSITION_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactAffiliation() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setAffiliation(generateStringOfLength(256));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + AFFILIATION_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactJurisdiction() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setJurisdiction(generateStringOfLength(256));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + JURISDICTION_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactEmail() {
        study = createStudy();
        Contact c1 = createContact();
        c1.setEmail(generateStringOfLength(256));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study, CONTACTS_FIELD + "[0]." + EMAIL_FIELD, getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactPlaceName() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setPlaceName(generateStringOfLength(256));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));

        assertValidatorMessage(validator, study, 
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + PLACE_NAME_FIELD, 
                getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactStreet() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setStreet(generateStringOfLength(256));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study,
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + STREET_FIELD,
                getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactDivision() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setDivision(generateStringOfLength(256));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study,
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + DIVISION_FIELD,
                getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactMailRouting() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setMailRouting(generateStringOfLength(256));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study,
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + MAIL_ROUTING_FIELD,
                getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactCity() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setCity(generateStringOfLength(256));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study,
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + CITY_FIELD,
                getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_contactPostalCode() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setPostalCode(generateStringOfLength(51));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study,
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + POSTAL_CODE_FIELD,
                getInvalidStringLengthMessage(50));
    }
    
    @Test
    public void stringLengthValidation_contactCountry() {
        study = createStudy();
        Contact c1 = createContact();
        Address a1 = createAddress();
        a1.setCountry(generateStringOfLength(256));
        c1.setAddress(a1);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(validator, study,
                CONTACTS_FIELD + "[0]." + ADDRESS_FIELD + "." + COUNTRY_FIELD,
                getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_studyDesignType() {
        study = createStudy();
        String designType = generateStringOfLength(256);
        study.setStudyDesignTypes(ImmutableSet.of(designType));
        assertValidatorMessage(validator, study, "studyDesignTypes["+designType+"]", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_disease() {
        study = createStudy();
        String disease = generateStringOfLength(256);
        study.setDiseases(ImmutableSet.of(disease));
        assertValidatorMessage(validator, study, "diseases["+disease+"]", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void jsonLengthValidation_clientData() {
        study = createStudy();
        study.setClientData(getExcessivelyLargeClientData());
        assertValidatorMessage(validator, study, "clientData", getInvalidStringLengthMessage(TEXT_SIZE));
    }
    
    @Test
    public void scheduleGuidNotFund() { 
        study = createStudy();
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(Optional.empty());
        
        assertValidatorMessage(validator, study, SCHEDULE_GUID_FIELD, SCHEDULE_GUID_INVALID_MSG);
    }

    @Test
    public void scheduleGuidWrongOrgId() {
        study = createStudy();
        study.setScheduleGuid(SCHEDULE_GUID);
        
        Schedule2 schedule = new Schedule2();
        schedule.setOwnerId(TEST_ORG_ID);
        when(mockScheduleService.getScheduleForStudyValidator(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(Optional.of(schedule));
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(false);
        
        assertValidatorMessage(validator, study, SCHEDULE_GUID_FIELD, SCHEDULE_GUID_OWNER_ERROR_MSG);
    }

    @Test
    public void scheduleGuidCorrectOrgId() {
        study = createStudy();
        study.setScheduleGuid(SCHEDULE_GUID);

        Schedule2 schedule = new Schedule2();
        schedule.setOwnerId(TEST_ORG_ID);
        when(mockScheduleService.getScheduleForStudyValidator(TEST_APP_ID, SCHEDULE_GUID)).thenReturn(Optional.of(schedule));
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(true);

        Validate.entityThrowingException(validator, study);
    }

    @Test
    public void scheduleGuidScheduleUnauthorized() { 
        study = createStudy();
        study.setScheduleGuid(SCHEDULE_GUID);
        
        when(mockScheduleService.getScheduleForStudyValidator(TEST_APP_ID, SCHEDULE_GUID)).thenThrow(new UnauthorizedException());
        
        assertValidatorMessage(validator, study, SCHEDULE_GUID_FIELD, SCHEDULE_GUID_OWNER_ERROR_MSG);
    }
    
    @Test
    public void studyStartEventIdInvalid() { 
        study = createStudy();
        study.setStudyStartEventId("foo");
        
        assertValidatorMessage(validator, study, STUDY_START_EVENT_ID_FIELD, INVALID_EVENT_ID);
    }
    
    @Test
    public void studyStartEventValidSystemEvent() { 
        study = createStudy();
        study.setStudyStartEventId("timeline_retrieved");
        
        entityThrowingException(validator, study);
    }
    
    @Test
    public void studyStartEventValidCustomEvent() {
        StudyCustomEvent event = new StudyCustomEvent();
        event.setEventId("foo");
        event.setUpdateType(IMMUTABLE);
        
        study = createStudy();
        study.setStudyStartEventId("foo");
        study.setCustomEvents(ImmutableList.of(event));
        
        entityThrowingException(validator, study);
        
        study = createStudy();
        study.setStudyStartEventId("custom:foo");
        study.setCustomEvents(ImmutableList.of(event));
        
        entityThrowingException(validator, study);
    }
    
    private Study createStudy() {
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
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
    
    private Address createAddress() {
        Address address = new Address();
        address.setPlaceName("place name");
        address.setStreet("street");
        address.setDivision("division");
        address.setMailRouting("mail routing");
        address.setCity("city");
        address.setPostalCode("postal code");
        address.setCountry("country");
        return address;
    }
}
