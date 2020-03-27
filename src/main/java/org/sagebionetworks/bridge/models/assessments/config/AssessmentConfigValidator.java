package org.sagebionetworks.bridge.models.assessments.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;

/**
 * This validator can be constructed with additional validators that will validate JsonNodes of a
 * specific declared type. Currently it only allows one validator per such type. Client developers 
 * are currently defining these types in their configuration system.
 *
 */
public class AssessmentConfigValidator implements Validator {
    
    public static final AssessmentConfigValidator INSTANCE = new AssessmentConfigValidator.Builder().build();

    private final Map<String, Validator> validators;
    
    private AssessmentConfigValidator(Map<String, Validator> validators) {
        this.validators = validators;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return AssessmentConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        AssessmentConfig assessmentConfig = (AssessmentConfig)target;
        
        if (assessmentConfig.getConfig() == null) {
            errors.rejectValue("config", "is required");
        } else {
            ConfigVisitor visitor = new ConfigVisitor(validators, errors);
            BridgeUtils.walk(assessmentConfig.getConfig(), visitor);
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
