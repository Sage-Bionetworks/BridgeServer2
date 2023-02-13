package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.models.studies.AlertIdCollection;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class AlertIdCollectionValidatorTest {
    private final AlertIdCollectionValidator validator = new AlertIdCollectionValidator();
    private static final String ALERT_ID = "test-alert-id";
    private AlertIdCollection alertIdCollection;

    @BeforeMethod
    public void beforeMethod() {
        alertIdCollection = new AlertIdCollection(ImmutableList.of(ALERT_ID));
    }

    @Test
    public void supports() {
        assertTrue(validator.supports(AlertIdCollection.class));
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(validator, alertIdCollection);
    }

    @Test
    public void valid_Empty() {
        alertIdCollection.setAlertIds(ImmutableList.of());
        Validate.entityThrowingException(validator, alertIdCollection);
    }

    @Test
    public void invalid_nullAlertIds() {
        alertIdCollection.setAlertIds(null);
        assertValidatorMessage(validator, alertIdCollection, "alertIds", CANNOT_BE_NULL);
    }
}
