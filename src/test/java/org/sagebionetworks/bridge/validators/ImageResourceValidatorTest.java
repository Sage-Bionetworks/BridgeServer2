package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ImageResource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
public class ImageResourceValidatorTest {
    static final String INVALID_LANG = "%s is not a valid ISO 639 alpha-2 or alpha-3 language code";

    ImageResourceValidator validator;

    ImageResource imageResource;

    @BeforeMethod
    public void beforeMethod() {
        validator = new ImageResourceValidator();

        imageResource = new ImageResource();
        imageResource.setName("default");
        imageResource.setModule("sage_survey");
        imageResource.setLabel(new Label("en", "english label"));
    }

    @Test
    public void supports() {
        assertTrue(validator.supports(ImageResource.class));
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(validator, imageResource);
    }

    @Test
    public void validEmptyModule() {
        imageResource.setModule("");
        Validate.entityThrowingException(validator, imageResource);
    }

    @Test
    public void validNullModule() {
        imageResource.setModule(null);
        Validate.entityThrowingException(validator, imageResource);
    }

    @Test
    public void validNullLabel() {
        imageResource.setLabel(null);
        Validate.entityThrowingException(validator, imageResource);
    }

    @Test
    public void emptyName() {
        imageResource.setName("");
        assertValidatorMessage(validator, imageResource, "name", CANNOT_BE_BLANK);
    }

    @Test
    public void nullName() {
        imageResource.setName(null);
        assertValidatorMessage(validator, imageResource, "name", CANNOT_BE_BLANK);
    }

    @Test
    public void nameStringLength() {
        imageResource.setName(generateStringOfLength(256));
        assertValidatorMessage(validator, imageResource, "name", getInvalidStringLengthMessage(255));
    }

    @Test
    public void moduleStringLength() {
        imageResource.setModule(generateStringOfLength(256));
        assertValidatorMessage(validator, imageResource, "module", getInvalidStringLengthMessage(255));
    }

    @Test
    public void emptyLabelLang() {
        imageResource.setLabel(new Label("", "empty label"));
        assertValidatorMessage(validator, imageResource, "label.lang", CANNOT_BE_BLANK);
    }

    @Test
    public void nullLabelLang() {
        imageResource.setLabel(new Label(null, "null label"));
        assertValidatorMessage(validator, imageResource, "label.lang", CANNOT_BE_BLANK);
    }

    @Test
    public void invalidLabelLang() {
        imageResource.setLabel(new Label("yyyy", "invalid label"));
        assertValidatorMessage(validator, imageResource, "label.lang", INVALID_LANG);
    }

    @Test
    public void emptyLabelValue() {
        imageResource.setLabel(new Label("en", ""));
        assertValidatorMessage(validator, imageResource, "label.value", CANNOT_BE_BLANK);
    }

    @Test
    public void nullLabelValue() {
        imageResource.setLabel(new Label("en", null));
        assertValidatorMessage(validator, imageResource, "label.value", CANNOT_BE_BLANK);
    }
}
