package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.doReturn;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.function.Consumer;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.StudyService;

public class PasswordResetValidatorTest {
    
    PasswordResetValidator validator;
    
    @Mock
    StudyService studyService;
    
    @Mock
    App app;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(PasswordPolicy.DEFAULT_PASSWORD_POLICY).when(app).getPasswordPolicy();
        doReturn(app).when(studyService).getStudy(TEST_APP_ID);
        
        validator = new PasswordResetValidator();
        validator.setStudyService(studyService);
    }
    
    @Test
    public void supportsClass() {
        assertTrue(validator.supports(PasswordReset.class));
    }

    @Test
    public void validatesValid() {
        PasswordReset reset = new PasswordReset("P@ssword1`", "token", TEST_APP_ID);
        
        Validate.entityThrowingException(validator, reset);
    }
    
    @Test
    public void passwordRequired() {
        validate(new PasswordReset("", "token", TEST_APP_ID), (e) -> {
            assertError(e, "password", "is required");
        });
    }
    
    @Test
    public void spTokenRequired() {
        validate(new PasswordReset("asdfasdf", "", TEST_APP_ID), (e) -> {
            assertError(e, "sptoken", "is required");
        });
    }
    
    @Test
    public void studyRequired() {
        validate(new PasswordReset("asdfasdf", "token", ""), (e) -> {
            assertError(e, "study", "is required");
        });
    }
    
    @Test
    public void invalidPassword() {
        validate(new PasswordReset("e", "token", TEST_APP_ID), (e) -> {
            assertError(e, "password", "must be at least 8 characters",
                    "must contain at least one number (0-9)",
                    "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )",
                    "must contain at least one uppercase letter (A-Z)");
        });
    }

    private void validate(PasswordReset reset, Consumer<InvalidEntityException> consumer) {
        try {
            Validate.entityThrowingException(validator, reset);    
        } catch(InvalidEntityException e) {
            consumer.accept(e);
        }
    }
    
    private void assertError(InvalidEntityException e, String fieldName, String... messages) {
        for (int i=0; i < messages.length; i++) {
            String message = messages[i];
            assertEquals(e.getErrors().get(fieldName).get(i), fieldName + " " + message);
        }
    }
}
