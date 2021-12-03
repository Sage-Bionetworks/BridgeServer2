package org.sagebionetworks.bridge.models.assessments.config;

import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.HashMap;
import java.util.Map;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.validators.AbstractValidator;

/**
 * This validator can be constructed with additional validators that will validate JsonNodes of a
 * specific declared type. Currently it only allows one validator per type. Client developers 
 * will define these types in their configuration system.
 */
public class AssessmentConfigValidator extends AbstractValidator {
    
    public static final AssessmentConfigValidator INSTANCE = new AssessmentConfigValidator.Builder().build();

    private final Map<String, Validator> validators;
    
    private AssessmentConfigValidator(Map<String, Validator> validators) {
        this.validators = validators;
    }
    
    @Override
    public void validate(Object target, Errors errors) {
        AssessmentConfig assessmentConfig = (AssessmentConfig)target;
        
        if (assessmentConfig.getConfig() == null) {
            errors.rejectValue("config", "is required");
        } else {
            validateStringLength(errors, TEXT_SIZE, assessmentConfig.getConfig().toString(), "config");
            errors.pushNestedPath("config");
            ConfigVisitor visitor = new ConfigVisitor(validators, errors);
            BridgeUtils.walk(assessmentConfig.getConfig(), visitor);
            errors.popNestedPath();
        }
    }
    
    public static class Builder {
        private Map<String, Validator> validators = new HashMap<>();
        
        public Builder addValidator(String typeName, Validator validator) {
            validators.put(typeName, validator);
            return this;
        }
        public AssessmentConfigValidator build() {
            return new AssessmentConfigValidator(validators);
        }
    }
}
