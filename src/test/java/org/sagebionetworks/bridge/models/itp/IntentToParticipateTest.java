package org.sagebionetworks.bridge.models.itp;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.fasterxml.jackson.databind.JsonNode;

public class IntentToParticipateTest {
    
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    
    @Test
    public void canSerialize() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP.getMillis()).withConsentCreatedOn(TIMESTAMP.getMillis()).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
                .withEmail(EMAIL).withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withOsName("iOS").withConsentSignature(consentSignature).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(itp);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("email").textValue(), EMAIL);
        assertEquals(node.get("subpopGuid").textValue(), "subpopGuid");
        assertEquals(node.get("scope").textValue(), "all_qualified_researchers");
        assertEquals(node.get("osName").textValue(), "iPhone OS");
        assertEquals(node.get("type").textValue(), "IntentToParticipate");
        assertEquals(node.size(), 8);
        
        JsonNode phoneNode = node.get("phone");
        assertEquals(phoneNode.get("number").textValue(), PHONE.getNumber());
        assertEquals(phoneNode.get("regionCode").textValue(), "US");
        assertEquals(phoneNode.get("nationalFormat").textValue(), PHONE.getNationalFormat());
        assertEquals(phoneNode.get("type").textValue(), "Phone");
        assertEquals(phoneNode.size(), 4);
        
        JsonNode consentNode = node.get("consentSignature");
        assertEquals(consentNode.get("name").textValue(), "Consent Name");
        assertEquals(consentNode.get("birthdate").textValue(), "1980-10-10");
        assertEquals(consentNode.get("imageData").textValue(), "image-data");
        assertEquals(consentNode.get("imageMimeType").textValue(), "image/png");
        assertEquals(consentNode.get("consentCreatedOn").textValue(), TIMESTAMP.toString());
        assertEquals(consentNode.get("signedOn").textValue(), TIMESTAMP.toString());
        assertEquals(consentNode.get("type").textValue(), "ConsentSignature");
        assertEquals(consentNode.size(), 7);
        
        IntentToParticipate deser = BridgeObjectMapper.get().readValue(node.toString(), IntentToParticipate.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getPhone().getNationalFormat(), PHONE.getNationalFormat());
        assertEquals(deser.getEmail(), EMAIL);
        assertEquals(deser.getSubpopGuid(), "subpopGuid");
        assertEquals(deser.getOsName(), "iPhone OS");
        assertEquals(deser.getScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);

        ConsentSignature consentDeser = deser.getConsentSignature();
        assertEquals(consentDeser.getName(), "Consent Name");
        assertEquals(consentDeser.getBirthdate(), "1980-10-10");
        assertEquals(consentDeser.getImageData(), "image-data");
        assertEquals(consentDeser.getImageMimeType(), "image/png");
        assertEquals(consentDeser.getConsentCreatedOn(), TIMESTAMP.getMillis());
        assertEquals(consentDeser.getSignedOn(), TIMESTAMP.getMillis());
        
        Phone deserPhone = deser.getPhone();
        assertEquals(deserPhone.getNumber(), PHONE.getNumber());
        assertEquals(deserPhone.getRegionCode(), "US");
        assertEquals(deserPhone.getNationalFormat(), PHONE.getNationalFormat());
    }
    
    @Test
    public void canCopy() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP.getMillis()).withConsentCreatedOn(TIMESTAMP.getMillis()).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
                .withEmail(EMAIL).withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withOsName("iOS").withConsentSignature(consentSignature).build();

        IntentToParticipate copy = new IntentToParticipate.Builder().copyOf(itp).build();
        assertEquals(copy.getAppId(), itp.getAppId());
        assertEquals(copy.getPhone().getNumber(), itp.getPhone().getNumber());
        assertEquals(copy.getEmail(), itp.getEmail());
        assertEquals(copy.getSubpopGuid(), itp.getSubpopGuid());
        assertEquals(copy.getScope(), itp.getScope());
        assertEquals(copy.getOsName(), itp.getOsName());
        assertEquals(copy.getConsentSignature(), itp.getConsentSignature());
    }
    
    @Test
    public void osSynonyms() throws Exception {
        ConsentSignature consentSignature = new ConsentSignature.Builder().withName("Consent Name")
                .withBirthdate("1980-10-10").withImageData("image-data").withImageMimeType("image/png")
                .withSignedOn(TIMESTAMP.getMillis()).withConsentCreatedOn(TIMESTAMP.getMillis()).build();
        
        IntentToParticipate itp = new IntentToParticipate.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
                .withSubpopGuid("subpopGuid").withScope(SharingScope.ALL_QUALIFIED_RESEARCHERS).withOsName("iOS")
                .withConsentSignature(consentSignature).build();
        
        assertNotEquals("iOS", OperatingSystem.IOS); // iOS is a synonym...
        assertEquals(itp.getOsName(), OperatingSystem.IOS); // ... it is translated to the standard constant.
    }
}
