package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.Optional;

import static org.mockito.Mockito.when;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdService;

public class IdentifierUpdateValidatorTest {

    private static final String UPDATED_EMAIL = "updated@email.com";
    private static final String UPDATED_EXTERNAL_ID = "updatedExternalId";
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(TEST_STUDY, UPDATED_EXTERNAL_ID);
    
    @Mock
    private ExternalIdService externalIdService;

    private Study study; 
    
    private IdentifierUpdateValidator validator;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        validator = new IdentifierUpdateValidator(study, externalIdService);
        
    }
    
    @Test
    public void signInRequired() {
        IdentifierUpdate update = new IdentifierUpdate(null, UPDATED_EMAIL, null, UPDATED_EXTERNAL_ID, null);
        
        assertValidatorMessage(validator, update, "IdentifierUpdate", "requires a signIn object");
    }
    
    @Test
    public void signInErrorsNestedSignIn() {
        // Sign in with no password
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, UPDATED_EXTERNAL_ID, null);
        assertValidatorMessage(validator, update, "signIn.password", "is required");
    }
    
    @Test
    public void signInErrorsNestedReauthentication() {
        // Reauthentication with no study
        SignIn reauth = new SignIn.Builder().withEmail(EMAIL)
                .withReauthToken("ABDC").build();
        
        IdentifierUpdate update = new IdentifierUpdate(reauth, null, PHONE, UPDATED_EXTERNAL_ID, SYNAPSE_USER_ID);
        assertValidatorMessage(validator, update, "signIn.study", "is required");
    }
    
    @Test
    public void validEmailPasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withPassword(PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validPhonePasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withPhone(PHONE).withPassword(PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validReauthUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validSynapseExternalIdUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withPassword(PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null, SYNAPSE_USER_ID);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void noUpdatesInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null, null);
        assertValidatorMessage(validator, update, "IdentifierUpdate",
                "requires at least one updated identifier (email, phone, externalId, synapseUserId)");
    }
    
    @Test
    public void validPhoneUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("4082588569", "US"), null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validExternalIdUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        when(externalIdService.getExternalId(TEST_STUDY, "newExternalId"))
                .thenReturn(Optional.of(ExternalIdentifier.create(TEST_STUDY, "newExternalId")));
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, "newExternalId", null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void phoneInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("12334578990", "US"), null, null);
        assertValidatorMessage(validator, update, "phoneUpdate", "does not appear to be a phone number");
    }
    
    @Test
    public void emailInvalidValue() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "junk", null, null, null);
        assertValidatorMessage(validator, update, "emailUpdate", "does not appear to be an email address");
    }
    
    @Test
    public void synapseUserIdInvalidBlankValue() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).withPassword(PASSWORD)
                .build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null, "  ");
        assertValidatorMessage(validator, update, "synapseUserIdUpdate", "cannot be blank");
    }
    
    @Test
    public void synapseUserIdInvalidNonNumericalValue() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).withPassword(PASSWORD)
                .build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null, "1asdf3");
        assertValidatorMessage(validator, update, "synapseUserIdUpdate", "should be a string containing a positive integer");
    }
    
    @Test
    public void emailEmptyValue() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "", null, null, null);
        assertValidatorMessage(validator, update, "emailUpdate", "does not appear to be an email address");
    }
    
    @Test
    public void externalIdValidWithManagement() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), UPDATED_EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, UPDATED_EXTERNAL_ID, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void externalIdInvalidWithManagement() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), UPDATED_EXTERNAL_ID)).thenReturn(Optional.empty());
        
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, UPDATED_EXTERNAL_ID, null);
        assertValidatorMessage(validator, update, "externalIdUpdate", "is not a valid external ID");
    }
    
    @Test
    public void externalIdCannotBeBlank() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, "", null);
        assertValidatorMessage(validator, update, "externalIdUpdate", "cannot be blank");
    }
}
