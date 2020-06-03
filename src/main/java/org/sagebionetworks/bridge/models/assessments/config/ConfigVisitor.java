package org.sagebionetworks.bridge.models.assessments.config;

import static org.sagebionetworks.bridge.BridgeConstants.TYPE_FIELD_NAME;

import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validators should be registered with this visitor using the type name of the object
 * to be validated, or "*" to validate all nodes in the tree. Currently we don't 
 * validate anything about assessment configurations. 
 */
public class ConfigVisitor implements BiConsumer<String, JsonNode> {
    private Errors errors;
    private Map<String, Validator> validators;
    
    ConfigVisitor(Map<String, Validator> validators, Errors errors) {
        this.errors = errors;
        this.validators = (validators == null) ? ImmutableMap.of() : validators;
    }
    @Override
    public void accept(String fieldPath, JsonNode node) {
        errors.pushNestedPath(fieldPath);
        if (node.has(TYPE_FIELD_NAME)) {
            String typeName = node.get(TYPE_FIELD_NAME).textValue();
            Validator validator = validators.get(typeName);
            if (validator != null) {
                validator.validate(node, errors);
            }
        }
        Validator validator = validators.get("*");
        if (validator != null) {
            validator.validate(node, errors);
        }
        errors.popNestedPath();
    }
}
