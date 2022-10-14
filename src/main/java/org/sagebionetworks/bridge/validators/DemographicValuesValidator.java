package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;

public class DemographicValuesValidator implements Validator {
    private static final String DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE = "en";
    private static final String INVALID_CONFIGURATION = "invalid configuration for demographics validation";
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_NUMBER_VALUE_MIN = "invalid number value (less than specified min)";
    private static final String INVALID_NUMBER_VALUE_MAX = "invalid number value (greater than specified max)";

    private DemographicValuesValidationConfiguration configuration;

    public DemographicValuesValidator(DemographicValuesValidationConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return configuration != null && Demographic.class.isAssignableFrom(clazz);
    }

    // assumes demographic has already been validated by the DemographicValidator
    @Override
    public void validate(Object target, Errors errors) {
        Demographic demographic = (Demographic) target;

        // validate the configuration itself
        errors.pushNestedPath(getConfigurationNestedPath(demographic));
        Validate.entity(DemographicValuesValidationConfigurationValidator.INSTANCE, errors, configuration);
        errors.popNestedPath();

        try {
            switch (configuration.getValidationType()) {
                case ENUM:
                    validateEnum(demographic, errors);
                    break;
                case NUMBER_RANGE:
                    validateNumberRange(demographic, errors);
                    break;
                default:
                    break;
            }
        } catch (IOException | IllegalArgumentException e) {
            errors.rejectValue(demographic.getCategoryName(), INVALID_CONFIGURATION);
        }
    }

    private String getConfigurationNestedPath(Demographic demographic) {
        return "demographicsValidationConfiguration[" + demographic.getCategoryName() + "]";
    }

    private String getDemographicField(Demographic demographic, int index) {
        return "demographic[" + demographic.getCategoryName() + "][" + index + "]";
    }

    private void validateEnum(Demographic demographic, Errors errors)
            throws JsonParseException, JsonMappingException, IOException {
        errors.pushNestedPath(getConfigurationNestedPath(demographic));
        // workaround because ObjectMapper does not have treeToValue method that accepts
        // a TypeReference
        JsonParser tokens = BridgeObjectMapper.get().treeAsTokens(configuration.getValidationRules());
        JavaType type = BridgeObjectMapper.get().getTypeFactory()
                .constructType(new TypeReference<Map<String, Set<String>>>() {
                });
        // cannot be null because that is checked by
        // DemographicValuesValidationConfigurationValidator
        Map<String, Set<String>> enumValidationRules = BridgeObjectMapper.get().readValue(tokens, type);
        // currently only English supported
        Set<String> allowedValues = enumValidationRules.get(DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE);
        if (allowedValues == null) {
            // does not exist or was explicitly null
            // maybe there was a different language specified that wasn't English so no
            // error
            return;
        }
        errors.popNestedPath();
        // validate all values in the Demographic against the values in the
        // AppConfigElement
        for (int i = 0; i < demographic.getValues().size(); i++) {
            DemographicValue demographicValue = demographic.getValues().get(i);
            if (!allowedValues.contains(demographicValue.getValue())) {
                errors.rejectValue(getDemographicField(demographic, i), INVALID_ENUM_VALUE);
            }
        }
    }

    private void validateNumberRange(Demographic demographic, Errors errors)
            throws JsonProcessingException, IllegalArgumentException {
        errors.pushNestedPath(getConfigurationNestedPath(demographic));
        NumberRangeValidationRules numberRangeValidationRules = BridgeObjectMapper.get()
                .treeToValue(configuration.getValidationRules(), NumberRangeValidationRules.class);
        if (numberRangeValidationRules == null) {
            errors.rejectValue("validationRules", CANNOT_BE_NULL);
            return;
        }
        errors.popNestedPath();
        for (int i = 0; i < demographic.getValues().size(); i++) {
            DemographicValue demographicValue = demographic.getValues().get(i);
            double actualValue;
            try {
                actualValue = Double.parseDouble(demographicValue.getValue());
            } catch (NumberFormatException e) {
                // this value is not a double, but maybe some other value is in this category so
                // we don't want to error early in case that one needs to be checked
                continue;
            }
            if (numberRangeValidationRules.getMin() != null && actualValue < numberRangeValidationRules.getMin()) {
                errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_MIN);
            }
            if (numberRangeValidationRules.getMax() != null && actualValue > numberRangeValidationRules.getMax()) {
                errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_MAX);
            }
        }
    }

    private static class NumberRangeValidationRules {
        private Double min;
        private Double max;

        public Double getMin() {
            return min;
        }

        public Double getMax() {
            return max;
        }
    }
}
