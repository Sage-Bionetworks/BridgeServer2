package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.TestUtils.generateStringOfLength;
import static org.sagebionetworks.bridge.TestUtils.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.MEDIUMTEXT_SIZE;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.time.DateUtils;

public class ConsentSignatureValidatorTest {
    private static final DateTime NOW = DateTime.parse("2022-02-21T00:00:00.000Z");
    private static final long SIGNED_ON_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch();
    private ConsentSignatureValidator validator;
    
    @BeforeMethod
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        validator = new ConsentSignatureValidator(0);
    }

    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void nullName() {
        ConsentSignature sig = new ConsentSignature.Builder().withBirthdate("1970-01-01")
                .withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void emptyName() {
        ConsentSignature sig = new ConsentSignature.Builder().withBirthdate("1970-01-01")
                .withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void nullBirthdate() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }
    
    @Test
    public void nullBirthdateOKWithoutAgeLimit() {
        validator = new ConsentSignatureValidator(0);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        Validate.entityThrowingException(validator, sig);
    }

    @Test
    public void emptyBirthdate() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("")
                .withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void emptyImageData() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData("").withImageMimeType("image/fake").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        assertValidatorMessage(validator, sig, "imageData", "cannot be an empty string");
    }

    @Test
    public void emptyImageMimeType() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withImageMimeType("").withSignedOn(SIGNED_ON_TIMESTAMP)
                .build();
        assertValidatorMessage(validator, sig, "imageMimeType", "cannot be an empty string");
    }

    @Test
    public void imageDataWithoutMimeType() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withSignedOn(SIGNED_ON_TIMESTAMP).build();
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }        
    }

    @Test
    public void imageMimeTypeWithoutData() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageMimeType("image/fake").withSignedOn(SIGNED_ON_TIMESTAMP).build();
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }
    }

    @Test
    public void jsonNoName() throws Exception {
        ConsentSignature sig = new ConsentSignature.Builder().withBirthdate("1970-01-01").build();
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonNullName() throws Exception {
        String jsonStr = "{\"name\":null, \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonEmptyName() throws Exception {
        String jsonStr = "{\"name\":\"\", \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "name", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonNoBirthdate() throws Exception {
        validator = new ConsentSignatureValidator(18);
        String jsonStr = "{\"name\":\"test name\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonNullBirthdate() throws Exception {
        validator = new ConsentSignatureValidator(18);
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":null}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonEmptyBirthdate() throws Exception {
        validator = new ConsentSignatureValidator(18);
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }

    @Test
    public void jsonEmptyImageData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "imageData", "cannot be an empty string");
    }

    @Test
    public void jsonEmptyImageMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertValidatorMessage(validator, sig, "imageMimeType", "cannot be an empty string");
    }

    @Test
    public void jsonImageDataWithoutMimeType() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }
    }

    @Test
    public void jsonImageMimeTypeWithoutData() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        try {
            Validate.entityThrowingException(validator, sig);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains(
                    "ConsentSignature must specify imageData and imageMimeType if you specify either of them"));
        }
    }

    @Test
    public void minAgeLimitButNoBirthdate() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").build();
        assertValidatorMessage(validator, sig, "birthdate", "cannot be missing, null, or blank");
    }
    
    @Test
    public void minAgeLimitButBirthdateTooRecent() {
        String birthdate = NOW.minusYears(18).plusDays(1).toLocalDate().toString();
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate(birthdate).build();
        assertValidatorMessage(validator, sig, "birthdate", "too recent (the study requires participants to be 18 years of age or older).");
    }
    
    @Test
    public void minAgeLimitBirthdateOK() {
        String birthdate = NOW.minusYears(18).toLocalDate().toString();
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate(birthdate).build();
        Validate.entityThrowingException(validator, sig);
    }
    
    @Test
    public void minAgeLimitBirthdateGarbled() {
        validator = new ConsentSignatureValidator(18);
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("15 May 2018").build();
        assertValidatorMessage(validator, sig, "birthdate", "is invalid (required format: YYYY-MM-DD)");
    }
    
    @Test
    public void optionalBirthdateGarbled() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("15 May 2018").build();
        assertValidatorMessage(validator, sig, "birthdate", "is invalid (required format: YYYY-MM-DD)");
    }
    
    @Test
    public void stringValidation_name() {
        validator = new ConsentSignatureValidator(0);
        ConsentSignature sig = new ConsentSignature.Builder().withName(generateStringOfLength(256)).build();
        assertValidatorMessage(validator, sig, "name", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringValidation_imageData() {
        validator = new ConsentSignatureValidator(0);
        ConsentSignature sig = new ConsentSignature.Builder().withName("name")
                .withImageData(generateStringOfLength(MEDIUMTEXT_SIZE + 1))
                .withImageMimeType("place").build();
        assertValidatorMessage(validator, sig, "imageData", getInvalidStringLengthMessage(MEDIUMTEXT_SIZE));
    }
    
    @Test
    public void stringValidation_imageMimeType() {
        validator = new ConsentSignatureValidator(0);
        ConsentSignature sig = new ConsentSignature.Builder().withName("name")
                .withImageData("imageData")
                .withImageMimeType(generateStringOfLength(256)).build();
        assertValidatorMessage(validator, sig, "imageMimeType", getInvalidStringLengthMessage(255));
    }
}
