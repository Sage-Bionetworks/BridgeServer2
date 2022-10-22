package org.sagebionetworks.bridge.validators;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;

public class DemographicValuesValidator implements Validator {
    private static final Logger LOG = LoggerFactory.getLogger(DemographicValuesValidator.class);

    private static final String DEMOGRAPHICS_ENUM_DEFAULT_LANGUAGE = "en";
    private static final String INVALID_CONFIGURATION = "invalid configuration for demographics validation";
    private static final String INVALID_ENUM_VALUE = "invalid enum value";
    private static final String INVALID_MIN_LARGER_THAN_MAX = "invalid min (cannot be larger than specified max)";
    private static final String INVALID_MIN_OUT_OF_RANGE = "invalid min (out of range)";
    private static final String INVALID_MAX_OUT_OF_RANGE = "invalid max (out of range)";
    private static final String INVALID_NUMBER_VALUE_NOT_A_NUMBER = "invalid number value (not an acceptable number; consult the documentation to see what numbers are valid)";
    private static final String INVALID_NUMBER_VALUE_OUT_OF_RANGE = "invalid number value (out of range; this number and the min/max are too large or too small to be validated)";
    private static final String INVALID_NUMBER_VALUE_LESS_THAN_MIN = "invalid number value (less than specified min)";
    private static final String INVALID_NUMBER_VALUE_GREATER_THAN_MAX = "invalid number value (greater than specified max)";

    // [+-]?(?:(?:\d+\.?\d*(?:[eE][+-]?\d+)?)|(?:\.\d+(?:[eE][+-]?\d+)?))
    private static final String OPTIONAL_SIGN_REGEX = "[+-]?";
    private static final String EXPONENT_WITH_DIGITS_REGEX = "[eE]" + OPTIONAL_SIGN_REGEX + "\\d+";
    // this is a subset of the regex specified in
    // https://docs.oracle.com/javase/8/docs/api/java/lang/Double.html#valueOf-java.lang.String-
    // see also:
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.10.2
    private static final Pattern NUMBER_REGEX = Pattern.compile(
            // optional sign
            OPTIONAL_SIGN_REGEX + "(?:" +
            // ONE OF a) at least 1 digit (if not then this group could match an empty
            // string), optional decimal, and 0 or more digits
            // Digits ._opt Digits_opt ExponentPart_opt
                    "(?:\\d+\\.?\\d*" +
                    // optional exponent part, sign, and additional digits
                    "(?:" + EXPONENT_WITH_DIGITS_REGEX + ")?)" +
                    // OR b) a decimal point then at least 1 digit (this is to allow decimals
                    // without a 0 before the decimal point)
                    // . Digits ExponentPart_opt
                    "|(?:\\.\\d+" +
                    // optional exponent part, sign, and additional digits
                    "(?:" + EXPONENT_WITH_DIGITS_REGEX + ")?))");

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
                && numberRangeValidationRules.getMin() > numberRangeValidationRules.getMax()) {
            errors.rejectValue("min", INVALID_MIN_LARGER_THAN_MAX);
        }
        if (numberRangeValidationRules.getMin() != null
                && numberRangeValidationRules.getMin() == Double.POSITIVE_INFINITY) {
            errors.rejectValue("min", INVALID_MIN_OUT_OF_RANGE);
        }
        if (numberRangeValidationRules.getMax() != null
                && numberRangeValidationRules.getMax() == Double.NEGATIVE_INFINITY) {
            errors.rejectValue("max", INVALID_MAX_OUT_OF_RANGE);
        }
        errors.popNestedPath();
        for (int i = 0; i < demographic.getValues().size(); i++) {
            DemographicValue demographicValue = demographic.getValues().get(i);
            // check with regex here because java double parser allows other weird things
            // like "NaN", "Infinity", hex, and type suffixes
            if (!NUMBER_REGEX.matcher(demographicValue.getValue()).matches()) {
                errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_NOT_A_NUMBER);
                continue;
            }
            double actualValue;
            try {
                actualValue = Double.parseDouble(demographicValue.getValue());
            } catch (NumberFormatException e) {
                // this should never happpen because the regex is stricter than the java double
                // parser
                errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_NOT_A_NUMBER);
                continue;
            }

            if (numberRangeValidationRules.getMin() != null) {
                if ((numberRangeValidationRules.getMin() == Double.NEGATIVE_INFINITY
                        || numberRangeValidationRules.getMin() == Double.POSITIVE_INFINITY)
                        && numberRangeValidationRules.getMin() == actualValue) {
                    // edge case: both actual value and min rounded to +/-inf when attempting to
                    // represent in double form, not possible to compare them
                    errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_OUT_OF_RANGE);
                    LOG.info(
                            "rejected value during demographics value validation because the value and min both rounded to +/-inf; appId "
                                    + demographic.getDemographicUser().getAppId() + " studyId "
                                    + demographic.getDemographicUser().getStudyId() + " userId "
                                    + demographic.getDemographicUser().getUserId());
                } else if (actualValue < numberRangeValidationRules.getMin()) {
                    errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_LESS_THAN_MIN);
                }
            }
            if (numberRangeValidationRules.getMax() != null) {
                if ((numberRangeValidationRules.getMax() == Double.NEGATIVE_INFINITY
                        || numberRangeValidationRules.getMax() == Double.POSITIVE_INFINITY)
                        && numberRangeValidationRules.getMax() == actualValue) {
                    // edge case: both actual value and min rounded to inf when attempting to
                    // represent in double form, not possible to compare them
                    errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_OUT_OF_RANGE);
                    LOG.info(
                            "rejected value during demographics value validation because the value and max both rounded to +/-inf; appId "
                                    + demographic.getDemographicUser().getAppId() + " studyId "
                                    + demographic.getDemographicUser().getStudyId() + " userId "
                                    + demographic.getDemographicUser().getUserId());
                } else if (actualValue > numberRangeValidationRules.getMax()) {
                    errors.rejectValue(getDemographicField(demographic, i), INVALID_NUMBER_VALUE_GREATER_THAN_MAX);
                }
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
