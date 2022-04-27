package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.apps.PasswordPolicy.DEFAULT_PASSWORD_POLICY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.thymeleaf.util.StringUtils;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableSet;

public class AdminAccountValidatorTest {
    
    AdminAccountValidator validator;
    
    @BeforeTest
    public void beforeTest( ) {
        validator = new AdminAccountValidator(DEFAULT_PASSWORD_POLICY, ImmutableSet.of("a"));
    }
    
    @Test
    public void valid() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        account.getAttributes().put("a", "test");
        account.setPassword("P@ssword1");
        account.setClientTimeZone("America/Los_Angeles");
        
        Validate.entityThrowingException(validator, account);
    }
    
    @Test
    public void validEmailAccount() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        
        Validate.entityThrowingException(validator, account);
    }
    
    @Test
    public void validPhone() {
        Account account = Account.create();
        account.setPhone(PHONE);
        
        Validate.entityThrowingException(validator, account);
    }
    
    @Test
    public void validSynapseUserId() {
        Account account = Account.create();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        
        Validate.entityThrowingException(validator, account);
    }
    
    @Test
    public void missingIdentifier() {
        Account account = Account.create();
        
        assertValidatorMessage(validator, account, "Account", "email, phone, or synapseUserId is required");
    }

    @Test
    public void phoneInvalid() {
        Account account = Account.create();
        account.setPhone(new Phone("1231231234", "US"));
        
        assertValidatorMessage(validator, account, "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void emailInvalid() {
        Account account = Account.create();
        account.setEmail("@test.com");
        
        assertValidatorMessage(validator, account, "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailBlank() {
        Account account = Account.create();
        account.setEmail("");
        
        assertValidatorMessage(validator, account, "email", "does not appear to be an email address");
    }
    
    @Test
    public void passwordInvalid() { 
        Account account = Account.create();
        account.setPassword("Password@");
        
        assertValidatorMessage(validator, account, "password", "must contain at least one number (0-9)");
    }
    
    @Test
    public void passwordBlank() {
        Account account = Account.create();
        account.setPassword("   ");
        
        assertValidatorMessage(validator, account, "password", "is required");
    }
    
    @Test
    public void synapseUserIdBlank() {
        Account account = Account.create();
        account.setSynapseUserId("\n\r\t");
        
        assertValidatorMessage(validator, account, "synapseUserId", Validate.CANNOT_BE_BLANK);
    }

    @Test
    public void userAttributeInvalid() {
        Account account = Account.create();
        account.getAttributes().put("b", "test");
        
        assertValidatorMessage(validator, account, "attributes", "'b' is not defined for app (use a)");
    }
    
    @Test
    public void userAttributeTooLong() {
        Account account = Account.create();
        account.getAttributes().put("a", StringUtils.randomAlphanumeric(256));
        
        assertValidatorMessage(validator, account, "attributes[a]", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void timeZoneInvalid() {
        Account account = Account.create();
        account.setClientTimeZone("America/Failsville");
        
        assertValidatorMessage(validator, account, "clientTimeZone",
                "is not a recognized IANA time zone name (eg. “America/Los_Angeles”)");
    }
    
    @Test
    public void emailTooLong() {
        Account account = Account.create();
        account.setEmail(StringUtils.randomAlphanumeric(256));
        
        assertValidatorMessage(validator, account, "email", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void firstNameTooLong() {
        Account account = Account.create();
        account.setFirstName(StringUtils.randomAlphanumeric(256));
        
        assertValidatorMessage(validator, account, "firstName", getInvalidStringLengthMessage(255));
    }

    @Test
    public void lastNameTooLong() {
        Account account = Account.create();
        account.setLastName(StringUtils.randomAlphanumeric(256));
        
        assertValidatorMessage(validator, account, "lastName", getInvalidStringLengthMessage(255));
    }

    @Test
    public void clientDataTooLong() {
        TextNode text = JsonNodeFactory.instance.textNode(StringUtils.randomAlphanumeric(TEXT_SIZE+1));
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("field", text);
        
        Account account = Account.create();
        account.setClientData(node);
        
        assertValidatorMessage(validator, account, "clientData", getInvalidStringLengthMessage(TEXT_SIZE));
    }
}
