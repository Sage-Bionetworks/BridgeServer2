package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.ImmutableSet;

public class ExternalIdValidatorTest {
    
    private ExternalIdValidator validatorV4;
    
    @Mock
    private StudyService studyService;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        validatorV4 = new ExternalIdValidator(studyService, false);
    }
    
    @AfterMethod
    public void after() {
        RequestContext.set(null);
    }
    
    @Test
    public void validates() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerStudies(ImmutableSet.of("study-id"))
                .withCallerAppId(TEST_APP_ID).build());
        
        when(studyService.getStudy(TEST_APP_ID, "study-id", false)).thenReturn(Study.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        id.setStudyId("study-id");
        
        Validate.entityThrowingException(validatorV4, id);
    }
    
    @Test
    public void validatesV3() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID).build());
        
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        
        ExternalIdValidator validatorV3 = new ExternalIdValidator(studyService, true);
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
    public void studyIdCannotBeNull() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        assertValidatorMessage(validatorV4, id, "studyId", "cannot be null or blank");
    }
    
    @Test
    public void studyIdCannotBeBlank() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        id.setStudyId("   ");
        assertValidatorMessage(validatorV4, id, "studyId", "cannot be null or blank");
    }
    
    @Test
    public void studyIdMustBeValid() {
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "identifier");
        id.setStudyId("not-real");
        assertValidatorMessage(validatorV4, id, "studyId", "is not a valid study");
    }
    
    @Test
    public void studyIdCanBeAnythingForAdmins() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN))
                .withCallerAppId(TEST_APP_ID).build());
        
        when(studyService.getStudy(TEST_APP_ID, "study-id", false)).thenReturn(Study.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        id.setStudyId("study-id");
        
        Validate.entityThrowingException(validatorV4, id);
    }
    
    @Test
    public void studyIdMustMatchCallersStudies() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("studyB"))
                .withCallerAppId(TEST_APP_ID).build());
        
        when(studyService.getStudy(TEST_APP_ID, "study-id", false)).thenReturn(Study.create());
        ExternalIdentifier id = ExternalIdentifier.create(TEST_APP_ID, "one-id");
        id.setStudyId("study-id");
        
        assertValidatorMessage(validatorV4, id, "studyId", "is not a valid study");
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
