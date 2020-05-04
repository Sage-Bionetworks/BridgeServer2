package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;

import com.google.common.collect.ImmutableSet;

public class ExternalIdValidatorTest {
    
    private ExternalIdValidator validatorV4;
    
    @Mock
    private SubstudyService substudyService;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        validatorV4 = new ExternalIdValidator(substudyService, false);
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void validates() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudy-id"))
                .withCallerAppId(TEST_APP_ID).build());
        
        when(substudyService.getSubstudy(TEST_APP_ID, "substudy-id", false)).thenReturn(Substudy.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        id.setSubstudyId("substudy-id");
        
        Validate.entityThrowingException(validatorV4, id);
    }
    
    @Test
    public void validatesV3() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID).build());
        
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        
        ExternalIdValidator validatorV3 = new ExternalIdValidator(substudyService, true);
        Validate.entityThrowingException(validatorV3, id);
    }
    
    @Test
    public void identifierCannotBeNull() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, null);
        assertValidatorMessage(validatorV4, id, "identifier", "cannot be null or blank");
    }
    
    @Test
    public void identifierCannotBeBlank() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "\t");
        assertValidatorMessage(validatorV4, id, "identifier", "cannot be null or blank");
    }
    
    @Test
    public void identifierMustMatchPattern1() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "two words");
        assertValidatorMessage(validatorV4, id, "identifier",
            "'two words' must contain only digits, letters, underscores and dashes");
    }
    
    @Test
    public void identifierMustMatchPattern2() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "<Funky>Markup");
        assertValidatorMessage(validatorV4, id, "identifier",
            "'<Funky>Markup' must contain only digits, letters, underscores and dashes");
    }

    @Test
    public void identifierMustMatchPattern3() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "And a \ttab character");
        assertValidatorMessage(validatorV4, id, "identifier",
            "'And a \ttab character' must contain only digits, letters, underscores and dashes");
    }
    
    @Test
    public void substudyIdCannotBeNull() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        assertValidatorMessage(validatorV4, id, "substudyId", "cannot be null or blank");
    }
    
    @Test
    public void substudyIdCannotBeBlank() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        id.setSubstudyId("   ");
        assertValidatorMessage(validatorV4, id, "substudyId", "cannot be null or blank");
    }
    
    @Test
    public void substudyIdMustBeValid() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        id.setSubstudyId("not-real");
        assertValidatorMessage(validatorV4, id, "substudyId", "is not a valid substudy");
    }
    
    @Test
    public void substudyIdCanBeAnythingForAdmins() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN))
                .withCallerAppId(TEST_APP_ID).build());
        
        when(substudyService.getSubstudy(TEST_APP_ID, "substudy-id", false)).thenReturn(Substudy.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        id.setSubstudyId("substudy-id");
        
        Validate.entityThrowingException(validatorV4, id);
    }
    
    @Test
    public void substudyIdMustMatchCallersSubstudies() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyB"))
                .withCallerAppId(TEST_APP_ID).build());
        
        when(substudyService.getSubstudy(TEST_APP_ID, "substudy-id", false)).thenReturn(Substudy.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        id.setSubstudyId("substudy-id");
        
        assertValidatorMessage(validatorV4, id, "substudyId", "is not a valid substudy");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void appIdCannotBeBlank() { 
        ExternalIdentifier.create("   ", "one-id");
    }
    
    @Test
    public void appIdMustBeCallersAppId() { 
        // This fails because we have not set a context with this app ID.
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        
        assertValidatorMessage(validatorV4, id, "appId", "is not a valid app");
    }
}
