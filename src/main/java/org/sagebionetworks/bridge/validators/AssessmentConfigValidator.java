package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.ID_FIELD_NAME;
import static org.sagebionetworks.bridge.BridgeConstants.TYPE_FIELD_NAME;

import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.assessments.AssessmentConfig;

public class AssessmentConfigValidator implements Validator {
    
    public static final AssessmentConfigValidator INSTANCE = new AssessmentConfigValidator();

    static class ConfigVisitor implements BiConsumer<String, JsonNode> {
        private Errors errors;
        private Map<String, Validator> validators;
        
        ConfigVisitor(Errors errors, Map<String, Validator> validators) {
            this.errors = errors;
            this.validators = (validators == null) ? ImmutableMap.of() : validators;
        }
        @Override
        public void accept(String fieldPath, JsonNode node) {
            errors.pushNestedPath(fieldPath);
            if (!node.has(ID_FIELD_NAME)) {
                errors.rejectValue(ID_FIELD_NAME, "is missing");
            }
            if (!node.has(TYPE_FIELD_NAME)) {
                errors.rejectValue(TYPE_FIELD_NAME, "is missing");
            } else {
                Validator validator = validators.get(node.get(TYPE_FIELD_NAME).textValue());
                if (validator != null) {
                    validator.validate(node, errors);    
                }
            }
            errors.popNestedPath();
        }
    }
    
    private Map<String, Validator> validators;
    
    void setValidators(Map<String, Validator> validators) {
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
            ConfigVisitor visitor = new ConfigVisitor(errors, validators);
            BridgeUtils.walk(assessmentConfig.getConfig(), visitor);
        }
    }
}
