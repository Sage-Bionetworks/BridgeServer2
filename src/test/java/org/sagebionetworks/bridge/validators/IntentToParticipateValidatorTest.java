package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.IntentToParticipateValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

public class IntentToParticipateValidatorTest {

    private static final String SUBPOP_GUID = "subpopGuid";
    private static final String OS_NAME = "Android";
    private static final SharingScope SCOPE = SharingScope.SPONSORS_AND_PARTNERS;
    private static final ConsentSignature SIGNATURE = new ConsentSignature.Builder()
            .withName("Test Name")
            .withBirthdate("1985-02-02")
            .build();
    
    private IntentToParticipate.Builder builder() {
        return new IntentToParticipate.Builder()
                .withAppId(TEST_APP_ID)
                .withPhone(TestConstants.PHONE)
                .withSubpopGuid(SUBPOP_GUID)
                .withScope(SCOPE)
                .withOsName(OS_NAME)
                .withConsentSignature(SIGNATURE);
    }
    
    @Test
    public void validWithPhone() {
        IntentToParticipate intent = builder().build();
        Validate.entityThrowingException(INSTANCE, intent);
    }
    
    @Test
    public void validWithEmail() {
        IntentToParticipate intent = builder().withPhone(null).withEmail(TestConstants.EMAIL).build();
        Validate.entityThrowingException(INSTANCE, intent);
    }
    
    @Test
    public void appIdRequired() {
        IntentToParticipate intent = builder().withAppId(null).build();
        assertValidatorMessage(INSTANCE, intent, "appId", "is required");
    }
    
    @Test
    public void subpopRequired() {
        IntentToParticipate intent = builder().withSubpopGuid("").build();
        assertValidatorMessage(INSTANCE, intent, "subpopGuid", "is required");
    }

    @Test
    public void scopeRequired() {
        IntentToParticipate intent = builder().withScope(null).build();
        assertValidatorMessage(INSTANCE, intent, "scope", "is required");
    }
    
    @Test
    public void signatureRequired() {
        IntentToParticipate intent = builder().withConsentSignature(null).build();
        assertValidatorMessage(INSTANCE, intent, "consentSignature", "is required");
    }

    @Test
    public void phoneOrEmailRequired() {
        IntentToParticipate intent = builder().withPhone(null).build();
        assertValidatorMessage(INSTANCE, intent, "IntentToParticipate", "either phone or email is required");
    }
    
    @Test
    public void phoneAndEmailInvalid() {
        IntentToParticipate intent = builder().withEmail(TestConstants.EMAIL).build();
        assertValidatorMessage(INSTANCE, intent, "IntentToParticipate", "one of phone or email should be provided (not both)");
    }

    @Test
    public void phoneIsInvalid() {
        IntentToParticipate intent = builder().withPhone(new Phone("333333333", "US")).build();
        assertValidatorMessage(INSTANCE, intent, "phone", INVALID_PHONE_ERROR);
    }
    
    @Test
    public void emailInvalid() {
        IntentToParticipate intent = builder().withPhone(null).withEmail("bad-email").build();
        assertValidatorMessage(INSTANCE, intent, "email", INVALID_EMAIL_ERROR);
    }
}
