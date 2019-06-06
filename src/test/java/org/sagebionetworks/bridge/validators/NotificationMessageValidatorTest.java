package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class NotificationMessageValidatorTest {

    private NotificationMessage message;
    
    private void message(String subject, String msg) {
        message = new NotificationMessage.Builder().withSubject(subject).withMessage(msg).build();
    }
    
    @Test
    public void isValid() {
        message("s", "m");
        Validate.entityThrowingException(NotificationMessageValidator.INSTANCE, message);
    }
    
    @Test
    public void subjectRequired() {
        message(null, "m");
        testError("subject", " is required");
        
        message("", "m");
        testError("subject", " is required");
        
        message(" ", "m");
        testError("subject", " is required");
    }
    
    @Test
    public void mesageRequired() {
        message("s", null);
        testError("message", " is required");
        
        message("s", "");
        testError("message", " is required");
        
        message("s", "  ");
        testError("message", " is required");
    }

    private void testError(String fieldName, String error) {
        try {
            Validate.entityThrowingException(NotificationMessageValidator.INSTANCE, message);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get(fieldName).get(0), fieldName+error);
        }
    }

}
