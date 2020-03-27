package org.sagebionetworks.bridge.models.assessments.config;

import static org.sagebionetworks.bridge.BridgeConstants.ID_FIELD_NAME;
import static org.sagebionetworks.bridge.BridgeConstants.TYPE_FIELD_NAME;

import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

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
        // Very probably, not all nodes of the final configurations will have 
        // an identifier and type. We will move this to a sub-validator when
        // there is clarity on semantics.
        if (!node.has(ID_FIELD_NAME)) {
            errors.rejectValue(ID_FIELD_NAME, "is missing");
        }
        if (!node.has(TYPE_FIELD_NAME)) {
            errors.rejectValue(TYPE_FIELD_NAME, "is missing");
        } else {
            String typeName = node.get(TYPE_FIELD_NAME).textValue();
            Validator validator = validators.get(typeName);
            if (validator != null) {
                validator.validate(node, errors);    
            }
        }
        errors.popNestedPath();
    }
}
