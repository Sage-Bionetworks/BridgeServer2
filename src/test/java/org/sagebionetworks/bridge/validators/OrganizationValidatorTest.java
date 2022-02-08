package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.validators.OrganizationValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_IDENTIFIER_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.organizations.Organization;

public class OrganizationValidatorTest {

    @Test
    public void validOrganization() {
        Organization org = getOrganization();
        org.setAppId(TestConstants.TEST_APP_ID);
        org.setName("Name");
        org.setIdentifier("an-org-id");
        
        Validate.entityThrowingException(INSTANCE, org);
    }
    
    @Test
    public void appIdNull() {
        Organization org = getOrganization();
        org.setAppId(null);
        assertValidatorMessage(INSTANCE, org, "appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void appIdBlank() {
        Organization org = getOrganization();
        org.setAppId(" ");
        assertValidatorMessage(INSTANCE, org, "appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void identifierNull() {
        Organization org = getOrganization();
        org.setIdentifier(null);
        assertValidatorMessage(INSTANCE, org, "identifier", CANNOT_BE_BLANK);
    }
    
    @Test
    public void identifierBlank() {
        Organization org = getOrganization();
        org.setIdentifier(" ");
        assertValidatorMessage(INSTANCE, org, "identifier", CANNOT_BE_BLANK);
    }
    
    @Test
    public void identifierInvalid() {
        Organization org = getOrganization();
        org.setIdentifier("this is invalid");
        assertValidatorMessage(INSTANCE, org, "identifier", BRIDGE_IDENTIFIER_ERROR);
    }
    
    @Test
    public void nameNull() {
        Organization org = getOrganization();
        org.setName(null);
        assertValidatorMessage(INSTANCE, org, "name", CANNOT_BE_BLANK);
    }
    
    @Test
    public void nameBlank() {
        Organization org = getOrganization();
        org.setName(" ");
        assertValidatorMessage(INSTANCE, org, "name", CANNOT_BE_BLANK);
    }
    
    @Test
    public void stringLengthValidation_identifier() {
        Organization org = getOrganization();
        org.setIdentifier(generateStringOfLength(256));
        assertValidatorMessage(INSTANCE, org, "identifier", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_name() {
        Organization org = getOrganization();
        org.setName(generateStringOfLength(256));
        assertValidatorMessage(INSTANCE, org, "name", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_description() {
        Organization org = getOrganization();
        org.setDescription(generateStringOfLength(TEXT_SIZE + 1));
        assertValidatorMessage(INSTANCE, org, "description", getInvalidStringLengthMessage(TEXT_SIZE));
    }
    
    private Organization getOrganization() {
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier("anIdentifier");
        org.setName("aName");
        return org;
    }
    
}
