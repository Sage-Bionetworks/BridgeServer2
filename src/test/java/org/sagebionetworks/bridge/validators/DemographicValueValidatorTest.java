package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

public class DemographicValueValidatorTest {
    private DemographicValueValidator demographicValueValidator = new DemographicValueValidator();

    @Test
    public void supports() {
        assertTrue(demographicValueValidator.supports(DemographicValue.class));
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue("foo"));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(1.5));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(0.0));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(false));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(7));
        Validate.entityThrowingException(demographicValueValidator, new DemographicValue(0));
    }

    @Test
    public void blank() {
        assertValidatorMessage(demographicValueValidator, new DemographicValue(""), "value", CANNOT_BE_NULL_OR_EMPTY);
        assertValidatorMessage(demographicValueValidator, new DemographicValue(null), "value", CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void stringLength() {
        assertValidatorMessage(demographicValueValidator, new DemographicValue(generateStringOfLength(1025)), "value",
                getInvalidStringLengthMessage(1024));
    }
}
