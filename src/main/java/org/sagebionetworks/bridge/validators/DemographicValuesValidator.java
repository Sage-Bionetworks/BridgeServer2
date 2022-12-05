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
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This is NOT a validator to check if demographics have malformed inputs. For
 * those validators, see DemographicUserValidator, DemographicValidator, and
 * DemographicValueValidator.
 * 
 * This validator validates the values in demographics to ensure they meet
 * user-specified restrictions listed in a
 * DemographicValuesValidationConfiguration which is stored in an app config
 * element.
 * 
 * This does not use the Spring validator framework because errors are stored in
 * the validated object (Demographic) directly instead of being returned.
 */
public class DemographicValuesValidator {
    private static final String DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE = "en";
    private static final String INVALID_DATA = "invalid data";
    // currently not exposing configuration errors
    public static final String INVALID_CONFIGURATION = INVALID_DATA;
    private static final String INVALID_CONFIGURATION_BAD_LANGUAGE_CODE = INVALID_CONFIGURATION;
    private static final String INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX = INVALID_CONFIGURATION;
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number";
    private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than min)";
    private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number (larger than max)";

    private DemographicValuesValidationConfiguration configuration;

    public DemographicValuesValidator(DemographicValuesValidationConfiguration configuration) {
        this.configuration = configuration;
    }

    // assumes demographic has already been validated by the DemographicValidator
    public void validate(Demographic demographic) {
        // validate the configuration itself
        if (configuration == null) {
            invalidateDemographic(demographic, INVALID_CONFIGURATION);
            return;
        }
        if (configuration.getValidationType() == null || configuration.getValidationRules() == null
                || configuration.getValidationRules().isNull()) {
            invalidateDemographic(demographic, INVALID_CONFIGURATION);
            return;
        }

        // validate the values
        try {
            configuration.getValidationType().validate(configuration.getValidationRules(), demographic);
        } catch (IOException e) {
            // error reading rules JSON, should not usually happen
            invalidateDemographic(demographic, INVALID_CONFIGURATION);
        }
    }

    public static void invalidateDemographic(Demographic demographic, String reason) {
        for (DemographicValue demographicValue : demographic.getValues()) {
            demographicValue.setInvalidity(reason);
        }
    }

    public enum DemographicValuesValidationType {
        NUMBER_RANGE {
            @Override
            public void validate(JsonNode validationRules, Demographic demographic) throws JsonProcessingException {
                NumberRangeValidationRules numberRangeValidationRules = BridgeObjectMapper.get()
                        .treeToValue(validationRules, NumberRangeValidationRules.class);
                if (numberRangeValidationRules.getMin() != null && numberRangeValidationRules.getMax() != null
                        && numberRangeValidationRules.getMin().compareTo(numberRangeValidationRules.getMax()) > 0) {
                    // min cannot be larger than max in configuration
                    invalidateDemographic(demographic, INVALID_CONFIGURATION_MIN_LARGER_THAN_MAX);
                    return;
                }
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

                    if (numberRangeValidationRules.getMin() != null
                            && actualValue.compareTo(numberRangeValidationRules.getMin()) < 0) {
                        // number too small
                        demographicValue.setInvalidity(INVALID_NUMBER_VALUE_LESS_THAN_MIN);
                    }
                    if (numberRangeValidationRules.getMax() != null
                            && actualValue.compareTo(numberRangeValidationRules.getMax()) > 0) {
                        // number too large
                        demographicValue.setInvalidity(INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
                    }
                }
            }
        },
        ENUM {
            @Override
            public void validate(JsonNode validationRules, Demographic demographic)
                    throws JsonParseException, JsonMappingException, IOException {
                // workaround because ObjectMapper does not have treeToValue method that accepts
                // a TypeReference
                JsonParser tokens = BridgeObjectMapper.get().treeAsTokens(validationRules);
                JavaType type = BridgeObjectMapper.get().getTypeFactory()
                        .constructType(new TypeReference<Map<String, Set<String>>>() {
                        });
                Map<String, Set<String>> enumValidationRules = BridgeObjectMapper.get().readValue(tokens, type);
                // validate languages
                for (String language : enumValidationRules.keySet()) {
                    Locale locale = new Locale.Builder().setLanguageTag(language).build();
                    if (!LocaleUtils.isAvailableLocale(locale)) {
                        // invalid language code
                        invalidateDemographic(demographic, INVALID_CONFIGURATION_BAD_LANGUAGE_CODE);
                        return;
                    }
                }
                // currently only English supported
                Set<String> allowedValues = enumValidationRules.get(DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE);
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
        };

        public abstract void validate(JsonNode validationRules, Demographic demographic)
                throws JsonParseException, JsonMappingException, IOException;

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
}
