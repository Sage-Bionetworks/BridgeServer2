package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;

import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.testng.annotations.Test;

public class DemographicValueValidatorTest {
    private final DemographicValueValidator demographicValueValidator = new DemographicValueValidator();

    /**
     * Tests that the validator supports the DemographicValue class.
     */
    @Test
    public void supports() {
        assertTrue(demographicValueValidator.supports(DemographicValue.class));
    }

    /**
     * Tests that the validator successfully validates a valid case.
     */
    @Test
    public void valid() {
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue("foo"));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(new BigDecimal("1.5")));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(new BigDecimal("0.0")));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(false));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(new BigDecimal("7")));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(new BigDecimal("0")));
    }

    /**
     * Tests that the validator rejects a DemographicValue with a blank value.
     */
    @Test
    public void blank() {
        assertValidatorMessage(demographicValueValidator, new DemographicValue(""), "value", CANNOT_BE_NULL_OR_EMPTY);
        assertValidatorMessage(demographicValueValidator, new DemographicValue((String) null), "value",
                CANNOT_BE_NULL_OR_EMPTY);
    }

    /**
     * Tests that the validator rejects a DemographicValue with an invalid value
     * length.
     */
    @Test
    public void stringLength() {
        assertValidatorMessage(demographicValueValidator, new DemographicValue(generateStringOfLength(1025)), "value",
                getInvalidStringLengthMessage(1024));
    }
}
