package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.studies.ContactRole.TECHNICAL_SUPPORT;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.entityThrowingException;

import com.google.common.collect.ImmutableList;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.Contact;
import org.sagebionetworks.bridge.models.studies.Study;

public class StudyValidatorTest {
    private static final StudyValidator VALIDATOR = StudyValidator.INSTANCE;
    
    private Study study;
    
    @Test
    public void valid() {
        study = Study.create();
        study.setIdentifier("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        study.setContacts(ImmutableList.of(createContact()));
        
        entityThrowingException(VALIDATOR, study);
    }
    
    @Test
    public void idIsRequired() {
        study = Study.create();
        assertValidatorMessage(VALIDATOR, study, "id", "is required");
    }
    
    @Test
    public void invalidIdentifier() {
        study = Study.create();
        study.setIdentifier("id not valid");
        
        assertValidatorMessage(VALIDATOR, study, "id", "must contain only lower- or upper-case letters, numbers, dashes, and/or underscores");
    }

    @Test
    public void nameIsRequired() {
        study = Study.create();
        assertValidatorMessage(VALIDATOR, study, "name", "is required");
    }
    
    @Test
    public void phaseRequired() {
        study = Study.create();
        study.setPhase(null);
        assertValidatorMessage(VALIDATOR, study, "phase", "is required");
    }

    @Test
    public void appIdIsRequired() {
        study = Study.create();
        assertValidatorMessage(VALIDATOR, study, "appId", "is required");
    }
    
    @Test
    public void contactNameNull() {
        study = Study.create();
        Contact c1 = createContact();
        c1.setName(null);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(VALIDATOR, study, "contacts[0].name", CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactNameBlank() {
        study = Study.create();
        Contact c1 = createContact();
        c1.setName("");
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(VALIDATOR, study, "contacts[0].name", CANNOT_BE_BLANK);
    }
    
    @Test
    public void contactRoleNull() {
        study = Study.create();
        Contact c1 = createContact();
        c1.setRole(null);
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(VALIDATOR, study, "contacts[0].role", CANNOT_BE_NULL);
    }
    
    @Test
    public void contactInvalidEmail() {
        study = Study.create();
        Contact c1 = createContact();
        c1.setEmail("junk");
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(VALIDATOR, study, "contacts[0].email", "does not appear to be an email address");
    }
    
    @Test
    public void contactInvalidPhone() {
        study = Study.create();
        Contact c1 = createContact();
        c1.setPhone(new Phone("333333", "Portual"));
        study.setContacts(ImmutableList.of(c1));
        
        assertValidatorMessage(VALIDATOR, study, "contacts[0].phone", "does not appear to be a phone number");
    }
    
    @Test
    public void nullContactsOK() {
        study = Study.create();
        study.setIdentifier("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        study.setContacts(null);
        
        entityThrowingException(VALIDATOR, study);
    }
    
    @Test
    public void nullContactEmailOK() {
        study = Study.create();
        study.setIdentifier("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        Contact c1 = createContact();
        c1.setEmail(null);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(VALIDATOR, study);
    }

    @Test
    public void nullContactPhoneOK() {
        study = Study.create();
        study.setIdentifier("id");
        study.setAppId(TEST_APP_ID);
        study.setName("name");
        Contact c1 = createContact();
        c1.setPhone(null);
        study.setContacts(ImmutableList.of(c1));
        
        entityThrowingException(VALIDATOR, study);
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
