package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DemographicValidatorTest {
    private Demographic demographicMultipleSelect;
    private Demographic demographicNotMultipleSelect;

    private DemographicValidator demographicValidator;

    @BeforeClass
    public void beforeClass() {
        demographicValidator = new DemographicValidator();
    }

    @BeforeMethod
    public void beforeMethod() {
        DemographicUser demographicUser = new DemographicUser();
        demographicUser.setId("test-demographic-user-id");
        demographicMultipleSelect = new Demographic("test-demographic-id", demographicUser, "test-category-name",
                true,
                new ArrayList<>(),
                "units");
        demographicNotMultipleSelect = new Demographic("test-demographic-id", demographicUser, "test-category-name",
                false,
                new ArrayList<>(),
                "units");
        demographicNotMultipleSelect.getValues().add(new DemographicValue("value"));
    }

    @Test
    public void supports() {
        assertTrue(demographicValidator.supports(Demographic.class));
    }

    @Test
    public void validMultipleSelect() {
        Validate.entityThrowingException(demographicValidator, demographicMultipleSelect);
    }

    @Test
    public void validMultipleSelectMultipleValues() {
        demographicMultipleSelect.getValues().add(new DemographicValue("value"));
        demographicMultipleSelect.getValues().add(new DemographicValue(1));
        demographicMultipleSelect.getValues().add(new DemographicValue(7.2));
        demographicMultipleSelect.getValues().add(new DemographicValue(true));
        Validate.entityThrowingException(demographicValidator, demographicMultipleSelect);
    }

    @Test
    public void validNotMultipleSelect() {
        Validate.entityThrowingException(demographicValidator, demographicNotMultipleSelect);
    }

    @Test
    public void blankId() {
        demographicMultipleSelect.setId(null);
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "id", CANNOT_BE_NULL_OR_EMPTY);
        demographicMultipleSelect.setId("");
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "id", CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void nullUser() {
        demographicMultipleSelect.setDemographicUser(null);
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "demographicUser",
                CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void blankUserId() {
        demographicMultipleSelect.getDemographicUser().setId(null);
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "demographicUser.id",
                CANNOT_BE_NULL_OR_EMPTY);
        demographicMultipleSelect.getDemographicUser().setId("");
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "demographicUser.id",
                CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void blankCategoryName() {
        demographicMultipleSelect.setCategoryName(null);
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "categoryName",
                CANNOT_BE_NULL_OR_EMPTY);
        demographicMultipleSelect.setCategoryName("");
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "categoryName",
                CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void nullValues() {
        demographicMultipleSelect.setValues(null);
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "values", CANNOT_BE_NULL);
    }

    @Test
    public void notMultipleSelectNotOneValue() {
        // now has 2 values
        demographicNotMultipleSelect.getValues().add(new DemographicValue("another value"));
        assertValidatorMessage(demographicValidator, demographicNotMultipleSelect, "values",
                DemographicValidator.MUST_HAVE_ONE_VALUE);
        // 0 values
        demographicNotMultipleSelect.getValues().clear();
        assertValidatorMessage(demographicValidator, demographicNotMultipleSelect, "values",
                DemographicValidator.MUST_HAVE_ONE_VALUE);
    }

    @Test
    public void containsNullValue() {
        demographicMultipleSelect.getValues().add(null);
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "values",
                DemographicValidator.CANNOT_CONTAIN_NULL);
        demographicNotMultipleSelect.getValues().set(0, null);
        assertValidatorMessage(demographicValidator, demographicNotMultipleSelect, "values",
                DemographicValidator.CANNOT_CONTAIN_NULL);
    }

    @Test
    public void idStringLength() {
        demographicMultipleSelect.setId(generateStringOfLength(61));
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "id",
                getInvalidStringLengthMessage(60));
    }

    @Test
    public void demographicUserIdStringLength() {
        demographicMultipleSelect.getDemographicUser().setId(generateStringOfLength(61));
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "demographicUser.id",
                getInvalidStringLengthMessage(60));
    }

    @Test
    public void categoryNameStringLength() {
        demographicMultipleSelect.setCategoryName(generateStringOfLength(769));
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "categoryName",
                getInvalidStringLengthMessage(768));
    }

    @Test
    public void unitsStringLength() {
        demographicMultipleSelect.setUnits(generateStringOfLength(513));
        assertValidatorMessage(demographicValidator, demographicMultipleSelect, "units",
                getInvalidStringLengthMessage(512));
    }
}
