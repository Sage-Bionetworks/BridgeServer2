package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.AppService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class PasswordResetValidator implements Validator {

    private AppService appService;
    
    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return PasswordReset.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        PasswordReset passwordReset = (PasswordReset)object;
        
        if (StringUtils.isBlank(passwordReset.getSptoken())) {
            errors.rejectValue("sptoken", "is required");
        }
        if (StringUtils.isBlank(passwordReset.getPassword())) {
            errors.rejectValue("password", "is required");
        }
        if (StringUtils.isBlank(passwordReset.getAppId())) {
            errors.rejectValue("study", "is required");
        }
        if (errors.hasErrors()) {
            return;
        }
        // This logic is now duplicated with StudyParticipant validation.
        App app = appService.getApp(passwordReset.getAppId());
        PasswordPolicy passwordPolicy = app.getPasswordPolicy();
        String password = passwordReset.getPassword();
        ValidatorUtils.validatePassword(errors, passwordPolicy, password);
    }
}
