package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DemographicUserValidatorTest {
    private DemographicUser demographicUser;
    private Demographic demographic;

    private final DemographicUserValidator demographicUserValidator = new DemographicUserValidator();

    @BeforeMethod
    public void beforeMethod() {
        demographicUser = new DemographicUser("test-id", "test-app-id", "test-study-id", "test-user-id",
                new HashMap<>());
        demographic = new Demographic("test-demographic-id", demographicUser, "category-name", true, new ArrayList<>(),
                "test-units");
    }

    @Test
    public void supports() {
        assertTrue(demographicUserValidator.supports(DemographicUser.class));
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(demographicUserValidator, demographicUser);
        demographicUser.getDemographics().put("category-name", demographic);
        Validate.entityThrowingException(demographicUserValidator, demographicUser);
    }

    @Test
    public void validNullStudy() {
        demographicUser.setStudyId(null);
        Validate.entityThrowingException(demographicUserValidator, demographicUser);
    }

    @Test
    public void blankId() {
        demographicUser.setId(null);
        assertValidatorMessage(demographicUserValidator, demographicUser, "id", CANNOT_BE_NULL_OR_EMPTY);
        demographicUser.setId("");
        assertValidatorMessage(demographicUserValidator, demographicUser, "id", CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void blankAppId() {
        demographicUser.setAppId(null);
        assertValidatorMessage(demographicUserValidator, demographicUser, "appId", CANNOT_BE_NULL_OR_EMPTY);
        demographicUser.setAppId("");
        assertValidatorMessage(demographicUserValidator, demographicUser, "appId", CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void blankUserId() {
        demographicUser.setUserId(null);
        assertValidatorMessage(demographicUserValidator, demographicUser, "userId", CANNOT_BE_NULL_OR_EMPTY);
        demographicUser.setUserId("");
        assertValidatorMessage(demographicUserValidator, demographicUser, "userId", CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void nullDemographics() {
        demographicUser.setDemographics(null);
        assertValidatorMessage(demographicUserValidator, demographicUser, "demographics", CANNOT_BE_NULL);
    }

    @Test
    public void demographicsKeyBlank() {
        demographicUser.getDemographics().put("", demographic);
        assertValidatorMessage(demographicUserValidator, demographicUser, "demographics key", CANNOT_BE_NULL_OR_EMPTY);
        demographicUser.getDemographics().put(null, demographic);
        assertValidatorMessage(demographicUserValidator, demographicUser, "demographics key", CANNOT_BE_NULL_OR_EMPTY);
    }

    @Test
    public void demographicsValueNull() {
        demographicUser.getDemographics().put("category-name", null);
        assertValidatorMessage(demographicUserValidator, demographicUser, "demographics value", CANNOT_BE_NULL);
    }

    @Test
    public void mistmatchedCategoryNameKey() {
        demographicUser.getDemographics().put("wrong-category-name", demographic);
        assertValidatorMessage(demographicUserValidator, demographicUser, DemographicUserValidator.KEYS_MUST_MATCH);
        demographicUser.getDemographics().clear();
        demographicUser.getDemographics().put("category-name", demographic);
        demographic.setCategoryName(null);
        assertValidatorMessage(demographicUserValidator, demographicUser, DemographicUserValidator.KEYS_MUST_MATCH);
    }

    @Test
    public void childDemographicDoesNotStoreParent() {
        demographic.setDemographicUser(new DemographicUser());
        demographicUser.getDemographics().put("category-name", demographic);
        assertValidatorMessage(demographicUserValidator, demographicUser,
                DemographicUserValidator.CHILD_MUST_STORE_PARENT);
        demographic.setDemographicUser(null);
        assertValidatorMessage(demographicUserValidator, demographicUser,
                DemographicUserValidator.CHILD_MUST_STORE_PARENT);
    }

    @Test
    public void idStringLength() {
        demographicUser.setId(generateStringOfLength(61));
        assertValidatorMessage(demographicUserValidator, demographicUser, "id", getInvalidStringLengthMessage(60));
    }

    @Test
    public void studyIdStringLength() {
        demographicUser.setStudyId(generateStringOfLength(61));
        assertValidatorMessage(demographicUserValidator, demographicUser, "studyId",
                getInvalidStringLengthMessage(60));
    }

    @Test
    public void appIdStringLength() {
        demographicUser.setAppId(generateStringOfLength(61));
        assertValidatorMessage(demographicUserValidator, demographicUser, "appId", getInvalidStringLengthMessage(60));
    }

    @Test
    public void userIdStringLength() {
        demographicUser.setUserId(generateStringOfLength(256));
        assertValidatorMessage(demographicUserValidator, demographicUser, "userId", getInvalidStringLengthMessage(255));
    }
}
