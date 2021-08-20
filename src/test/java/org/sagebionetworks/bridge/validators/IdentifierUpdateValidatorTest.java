package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.IdentifierUpdateValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;

import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class IdentifierUpdateValidatorTest {

    private static final String UPDATED_EMAIL = "updated@email.com";
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void signInRequired() {
        IdentifierUpdate update = new IdentifierUpdate(null, UPDATED_EMAIL, null, null);
        
        assertValidatorMessage(INSTANCE, update, "IdentifierUpdate", "requires a signIn object");
    }
    
    @Test
    public void signInErrorsNestedSignIn() {
        // Sign in with no password
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        assertValidatorMessage(INSTANCE, update, "signIn.password", "is required");
    }
    
    @Test
    public void signInErrorsNestedReauthentication() {
        // Reauthentication with no app
        SignIn reauth = new SignIn.Builder().withEmail(EMAIL)
                .withReauthToken("ABDC").build();
        
        IdentifierUpdate update = new IdentifierUpdate(reauth, null, PHONE, SYNAPSE_USER_ID);
        assertValidatorMessage(INSTANCE, update, "signIn.appId", "is required");
    }
    
    @Test
    public void validEmailPasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withPassword(PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(INSTANCE, update);
    }
    
    @Test
    public void validPhonePasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withPhone(PHONE).withPassword(PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(INSTANCE, update);
    }
    
    @Test
    public void validReauthUpdate() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(INSTANCE, update);
    }
    
    @Test
    public void validSynapseExternalIdUpdate() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withPassword(PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, SYNAPSE_USER_ID);
        Validate.entityThrowingException(INSTANCE, update);
    }
    
    @Test
    public void noUpdatesInvalid() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null);
        assertValidatorMessage(INSTANCE, update, "IdentifierUpdate",
                "requires at least one updated identifier (email, phone, externalId, synapseUserId)");
    }
    
    @Test
    public void validPhoneUpdate() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("4082588569", "US"), null);
        Validate.entityThrowingException(INSTANCE, update);
    }
    
    @Test
    public void phoneInvalid() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("12334578990", "US"), null);
        assertValidatorMessage(INSTANCE, update, "phoneUpdate", INVALID_PHONE_ERROR);
    }
    
    @Test
    public void emailInvalidValue() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "junk", null, null);
        assertValidatorMessage(INSTANCE, update, "emailUpdate", INVALID_EMAIL_ERROR);
    }
    
    @Test
    public void synapseUserIdInvalidBlankValue() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL).withPassword(PASSWORD)
                .build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, "  ");
        assertValidatorMessage(INSTANCE, update, "synapseUserIdUpdate", CANNOT_BE_BLANK);
    }
    
    @Test
    public void synapseUserIdInvalidNonNumericalValue() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL).withPassword(PASSWORD)
                .build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, "1asdf3");
        assertValidatorMessage(INSTANCE, update, "synapseUserIdUpdate", "should be a string containing a positive integer");
    }
    
    @Test
    public void emailEmptyValue() {
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withEmail(EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "", null, null);
        assertValidatorMessage(INSTANCE, update, "emailUpdate", INVALID_EMAIL_ERROR);
    }
}
