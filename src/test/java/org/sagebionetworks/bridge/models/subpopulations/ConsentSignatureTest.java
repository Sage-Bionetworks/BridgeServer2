package org.sagebionetworks.bridge.models.subpopulations;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignatureTest {
    private static final DateTime CONSENT_CREATED_ON_TIMESTAMP = DateTime.now(DateTimeZone.UTC).minusDays(1);
    private static final DateTime SIGNED_ON_TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    private static final DateTime WITHDREW_ON_TIMESTAMP = DateTime.now(DateTimeZone.UTC).plusDays(1);
    
    @BeforeMethod
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(SIGNED_ON_TIMESTAMP.getMillis());
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void allDatesFormattedCorrectly() throws Exception {
        ConsentSignature signature = new ConsentSignature.Builder()
            .withBirthdate("1970-01-01")
            .withName("Dave Test")
            .withWithdrewOn(WITHDREW_ON_TIMESTAMP.getMillis())
            .withConsentCreatedOn(CONSENT_CREATED_ON_TIMESTAMP.getMillis())
            .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        
        String json = ConsentSignature.SIGNATURE_WRITER.writeValueAsString(signature);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertNull(node.get("consentCreatedOn"));
        assertEquals(node.get("withdrewOn").textValue(), WITHDREW_ON_TIMESTAMP.toString());
        assertEquals(node.get("signedOn").textValue(), SIGNED_ON_TIMESTAMP.toString());
        assertEquals(node.get("type").textValue(), "ConsentSignature");
        
        ConsentSignature deser = ConsentSignature.fromJSON(node);
        assertEquals(deser.getName(), "Dave Test");
        assertEquals(deser.getBirthdate(), "1970-01-01");
        assertEquals(deser.getSignedOn(), SIGNED_ON_TIMESTAMP.getMillis()); // this is set in the builder
        assertEquals(deser.getConsentCreatedOn(), 0L);
        assertNull(deser.getWithdrewOn());
    }
    
    @Test
    public void happyCase() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withConsentCreatedOn(CONSENT_CREATED_ON_TIMESTAMP.getMillis())
                .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        assertEquals(sig.getName(), "test name");
        assertEquals(sig.getBirthdate(), "1970-01-01");
        assertEquals(sig.getConsentCreatedOn(), CONSENT_CREATED_ON_TIMESTAMP.getMillis());
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void withImage() {
        ConsentSignature sig = new ConsentSignature.Builder().withName("test name").withBirthdate("1970-01-01")
                .withImageData(TestConstants.DUMMY_IMAGE_DATA).withImageMimeType("image/fake")
                .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        assertEquals(sig.getName(), "test name");
        assertEquals(sig.getBirthdate(), "1970-01-01");
        assertEquals(sig.getImageData(), TestConstants.DUMMY_IMAGE_DATA);
        assertEquals(sig.getImageMimeType(), "image/fake");
    }

    @Test
    public void jsonHappyCase() throws Exception {
        String jsonStr = "{\"name\":\"test name\", \"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals(sig.getName(), "test name");
        assertEquals(sig.getBirthdate(), "1970-01-01");
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCaseNullImage() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":null,\n" +
                "   \"imageMimeType\":null\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals(sig.getName(), "test name");
        assertEquals(sig.getBirthdate(), "1970-01-01");
        assertNull(sig.getImageData());
        assertNull(sig.getImageMimeType());
    }

    @Test
    public void jsonHappyCaseWithImage() throws Exception {
        String jsonStr = "{\n" +
                "   \"name\":\"test name\",\n" +
                "   \"birthdate\":\"1970-01-01\",\n" +
                "   \"imageData\":\"" + TestConstants.DUMMY_IMAGE_DATA + "\",\n" +
                "   \"imageMimeType\":\"image/fake\"\n" +
                "}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(jsonStr, ConsentSignature.class);
        assertEquals(sig.getName(), "test name");
        assertEquals(sig.getBirthdate(), "1970-01-01");
        assertEquals(sig.getImageData(), TestConstants.DUMMY_IMAGE_DATA);
        assertEquals(sig.getImageMimeType(), "image/fake");
        assertEquals(sig.getSignedOn(), SIGNED_ON_TIMESTAMP.getMillis());
    }
    
    @Test
    public void existingSignatureJsonDeserializesWithoutSignedOn() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals(sig.getName(), "test name");
        assertEquals(sig.getBirthdate(), "1970-01-01");
        assertEquals(sig.getSignedOn(), SIGNED_ON_TIMESTAMP.getMillis());
    }
    
    @Test
    public void migrationConstructorUpdatesSignedOnValue() throws Exception {
        String json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\"}";
        ConsentSignature sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);

        ConsentSignature updated = new ConsentSignature.Builder().withConsentSignature(sig)
                .withSignedOn(SIGNED_ON_TIMESTAMP.getMillis()).build();
        assertEquals(updated.getName(), "test name");
        assertEquals(updated.getBirthdate(), "1970-01-01");
        assertEquals(updated.getSignedOn(), SIGNED_ON_TIMESTAMP.getMillis());
        
        json = "{\"name\":\"test name\",\"birthdate\":\"1970-01-01\",\"signedOn\":\"" + 
                SIGNED_ON_TIMESTAMP.toString() + "\"}";
        sig = BridgeObjectMapper.get().readValue(json, ConsentSignature.class);
        assertEquals(sig.getSignedOn(), SIGNED_ON_TIMESTAMP.getMillis());
    }
    
    @Test
    public void equalsAndHashCodeAreCorrect() {
        EqualsVerifier.forClass(ConsentSignature.class).allFieldsShouldBeUsed().verify();
    }
}
