package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.models.studies.Alert.AlertCategory;
import org.sagebionetworks.bridge.models.studies.AlertFilter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

public class AlertFilterValidatorTest {
    private final AlertFilterValidator validator = new AlertFilterValidator();
    private AlertFilter alertFilter;

    @BeforeMethod
    public void beforeMethod() {
        alertFilter = new AlertFilter(ImmutableSet.of(AlertCategory.NEW_ENROLLMENT));
    }

    @Test
    public void supports() {
        assertTrue(validator.supports(AlertFilter.class));
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(validator, alertFilter);
    }

    @Test
    public void valid_Empty() {
        alertFilter.setAlertCategories(ImmutableSet.of());
        Validate.entityThrowingException(validator, alertFilter);
    }

    @Test
    public void invalid_nullAlertCategories() {
        alertFilter.setAlertCategories(null);
        assertValidatorMessage(validator, alertFilter, "alertCategories", CANNOT_BE_NULL);
    }
}
