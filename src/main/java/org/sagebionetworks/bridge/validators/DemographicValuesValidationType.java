package org.sagebionetworks.bridge.validators;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This determines the type of demographics values validation to perform, as
 * well as the schema of the validation rules. It is stored along with the rules
 * in a DemographicValuesValidationConfig.
 */
public enum DemographicValuesValidationType {
    NUMBER_RANGE {
        @Override
        public DemographicValuesValidator getValidatorWithRules(JsonNode validationRules) throws IOException {
            return new NumberRangeValidator(validationRules);
        }
    },
    ENUM {
        @Override
        public DemographicValuesValidator getValidatorWithRules(JsonNode validationRules) throws IOException {
            return new EnumValidator(validationRules);
        }
    };

    public abstract DemographicValuesValidator getValidatorWithRules(JsonNode validationRules) throws IOException;

    private class EnumValidator implements DemographicValuesValidator {
        private static final String DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE = "en";
        private static final String INVALID_CONFIGURATION_BAD_LANGUAGE_CODE = "bad language code";
        private static final String INVALID_ENUM_VALUE = "invalid enum value";

        Map<String, Set<String>> deserializedRules;

        public EnumValidator(JsonNode validationRules) throws IOException {
            // workaround because ObjectMapper does not have treeToValue method that accepts
            // a TypeReference
            JsonParser tokens = BridgeObjectMapper.get().treeAsTokens(validationRules);
            JavaType type = BridgeObjectMapper.get().getTypeFactory()
                    .constructType(new TypeReference<Map<String, Set<String>>>() {
                    });
            deserializedRules = BridgeObjectMapper.get().readValue(tokens, type);
        }

        @Override
        public void validateRules(Errors errors) {
            // validate languages
            for (String language : deserializedRules.keySet()) {
                Locale locale = new Locale.Builder().setLanguageTag(language).build();
                if (!LocaleUtils.isAvailableLocale(locale)) {
                    // invalid language code
                    errors.rejectValue("languageCode", INVALID_CONFIGURATION_BAD_LANGUAGE_CODE);
                }
            }
        }

        @Override
        public void validateDemographicUsingRules(Demographic demographic) {
            // currently only English supported
            Set<String> allowedValues = deserializedRules.get(DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE);
            if (allowedValues == null) {
                // does not exist or was explicitly null
                // maybe there was a different language specified that wasn't English so no
                // error
                return;
            }
            // validate all values in the Demographic against the values in the
            // AppConfigElement
            for (int i = 0; i < demographic.getValues().size(); i++) {
                DemographicValue demographicValue = demographic.getValues().get(i);
                if (!allowedValues.contains(demographicValue.getValue())) {
                    demographicValue.setInvalidity(INVALID_ENUM_VALUE);
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

    private class NumberRangeValidator implements DemographicValuesValidator {
        private static final String INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX = "min cannot be larger than max";
        private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number";
        private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than min)";
        private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number (larger than max)";

        NumberRangeValidationRules deserializedRules;

        public NumberRangeValidator(JsonNode validationRules) throws IOException {
            deserializedRules = BridgeObjectMapper.get()
                    .treeToValue(validationRules, NumberRangeValidationRules.class);
        }

        @Override
        public void validateRules(Errors errors) {
            if (deserializedRules.getMin() != null && deserializedRules.getMax() != null
                    && deserializedRules.getMin().compareTo(deserializedRules.getMax()) > 0) {
                // min cannot be larger than max in configuration
                errors.rejectValue("minAndMax", INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX);
                return;
            }
        }

        @Override
        public void validateDemographicUsingRules(Demographic demographic) {
            for (int i = 0; i < demographic.getValues().size(); i++) {
                DemographicValue demographicValue = demographic.getValues().get(i);
                BigDecimal actualValue;
                try {
                    actualValue = new BigDecimal(demographicValue.getValue());
                } catch (NumberFormatException e) {
                    // not a valid number
                    demographicValue.setInvalidity(INVALID_NUMBER_VALUE_NOT_A_NUMBER);
                    continue;
                }

                if (deserializedRules.getMin() != null
                        && actualValue.compareTo(deserializedRules.getMin()) < 0) {
                    // number too small
                    demographicValue.setInvalidity(INVALID_NUMBER_VALUE_LESS_THAN_MIN);
                }
                if (deserializedRules.getMax() != null
                        && actualValue.compareTo(deserializedRules.getMax()) > 0) {
                    // number too large
                    demographicValue.setInvalidity(INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
                }
            }
        }
    }
}
