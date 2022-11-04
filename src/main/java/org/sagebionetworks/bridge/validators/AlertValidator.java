package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.alerts.Alert;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;


public class AlertValidator implements Validator {
    public static final AlertValidator INSTANCE = new AlertValidator();
    

    @Override
    public boolean supports(Class<?> clazz) {
        return Alert.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        // TODO Auto-generated method stub
        
    }
}
