package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.generateStringOfLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtilsTest.getInvalidStringLengthMessage;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ImageResource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

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
        imageResource.setLabels(LABELS);
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
        imageResource.setLabels(null);
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
    public void duplicateLabels() {
        imageResource.setLabels(ImmutableList.of(new Label("en", "foo"), new Label("en", "bar")));
        assertValidatorMessage(validator, imageResource, "labels[1].lang", DUPLICATE_LANG);
    }

    @Test
    public void emptyLabelLang() {
        imageResource.setLabels(ImmutableList.of(new Label("", "empty label")));
        assertValidatorMessage(validator, imageResource, "labels[0].lang", CANNOT_BE_BLANK);
    }

    @Test
    public void nullLabelLang() {
        imageResource.setLabels(ImmutableList.of(new Label(null, "null label")));
        assertValidatorMessage(validator, imageResource, "labels[0].lang", CANNOT_BE_BLANK);
    }

    @Test
    public void invalidLabelLang() {
        imageResource.setLabels(ImmutableList.of(new Label("yyyy", "invalid label")));
        assertValidatorMessage(validator, imageResource, "labels[0].lang", INVALID_LANG);
    }

    @Test
    public void emptyLabelValue() {
        imageResource.setLabels(ImmutableList.of(new Label("en", "")));
        assertValidatorMessage(validator, imageResource, "labels[0].value", CANNOT_BE_BLANK);
    }

    @Test
    public void nullLabelValue() {
        imageResource.setLabels(ImmutableList.of(new Label("en", null)));
        assertValidatorMessage(validator, imageResource, "labels[0].value", CANNOT_BE_BLANK);
    }
}
