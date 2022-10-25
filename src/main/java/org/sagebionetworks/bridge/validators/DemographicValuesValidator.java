package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import java.io.IOException;
import java.math.BigDecimal;
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

/**
 * This is NOT a validator to check if demographics have malformed inputs. For
 * those validators, see DemographicUserValidator, DemographicValidator, and
 * DemographicValueValidator.
 * 
 * This validator validates the values in demographics to ensure they meet
 * user-specified restrictions listed in a
 * DemographicValuesValidationConfiguration specified in an app config element.
 */
public class DemographicValuesValidator implements Validator {
    private static final String DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE = "en";
    private static final String INVALID_CONFIGURATION = "invalid configuration for demographics validation";
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_MIN_LARGER_THAN_MAX = "invalid min (cannot be larger than specified max)";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number value (not an acceptable number; consult the documentation to see what numbers are valid)";
    private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than specified min)";
    private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number value (greater than specified max)";

    private DemographicValuesValidationConfiguration configuration;

    public DemographicValuesValidator(DemographicValuesValidationConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return Demographic.class.isAssignableFrom(clazz);
    }

    // assumes demographic has already been validated by the DemographicValidator
    @Override
    public void validate(Object target, Errors errors) {
        Demographic demographic = (Demographic) target;

        // validate the configuration itself
        if (configuration == null) {
            errors.rejectValue(getConfigurationNestedPath(demographic), CANNOT_BE_NULL);
            return;
        }
        errors.pushNestedPath(getConfigurationNestedPath(demographic));
        Validate.entity(DemographicValuesValidationConfigurationValidator.INSTANCE, errors, configuration);
        errors.popNestedPath();
        if (errors.hasErrors()) {
            // configuration is invalid so we don't know how to validate the values
            return;
        }

        try {
            switch (configuration.getValidationType()) {
                case ENUM:
                    validateEnum(demographic, errors);
                    break;
                case NUMBER_RANGE:
                    validateNumberRange(demographic, errors);
                    break;
                default:
                    // should not be possible
                    break;
            }
        } catch (IOException | IllegalArgumentException e) {
            errors.setNestedPath("");
            errors.rejectValue(getConfigurationNestedPath(demographic), INVALID_CONFIGURATION);
        }
    }

    private String getConfigurationNestedPath(Demographic demographic) {
        return "demographicsValidationConfiguration[" + demographic.getCategoryName() + "]";
    }

    private String getDemographicField(Demographic demographic, int index) {
        return "demographics[" + demographic.getCategoryName() + "][" + index + "]";
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
        // cannot be null because that is checked by
        // DemographicValuesValidationConfigurationValidator
        NumberRangeValidationRules numberRangeValidationRules = BridgeObjectMapper.get()
                .treeToValue(configuration.getValidationRules(), NumberRangeValidationRules.class);
        if (numberRangeValidationRules.getMin() != null && numberRangeValidationRules.getMax() != null
                && numberRangeValidationRules.getMin().compareTo(numberRangeValidationRules.getMax()) > 0) {
            errors.rejectValue("min", INVALID_MIN_LARGER_THAN_MAX);
        }
        errors.popNestedPath();
        for (int i = 0; i < demographic.getValues().size(); i++) {
            DemographicValue demographicValue = demographic.getValues().get(i);
            BigDecimal actualValue;
            try {
                actualValue = new BigDecimal(demographicValue.getValue());
            } catch (NumberFormatException e) {
                errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_NOT_A_NUMBER);
                continue;
            }

            if (numberRangeValidationRules.getMin() != null) {
                if (actualValue.compareTo(numberRangeValidationRules.getMin()) < 0) {
                    errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_LESS_THAN_MIN);
                }
            }
            if (numberRangeValidationRules.getMax() != null) {
                if (actualValue.compareTo(numberRangeValidationRules.getMax()) > 0) {
                    errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
                }
            }
        }
    }

    private static class NumberRangeValidationRules {
        private BigDecimal min;
        private BigDecimal max;

        public BigDecimal getMin() {
            return min;
        }

        public BigDecimal getMax() {
            return max;
        }
    }
}
