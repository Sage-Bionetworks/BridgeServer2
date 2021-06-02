package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class PhoneTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(Phone.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Phone phone = new Phone(TestConstants.PHONE.getNationalFormat(), TestConstants.PHONE.getRegionCode());
        assertEquals(phone.getNumber(), TestConstants.PHONE.getNumber());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(phone);
        assertEquals(node.get("number").textValue(), TestConstants.PHONE.getNumber());
        assertEquals(node.get("regionCode").textValue(), TestConstants.PHONE.getRegionCode());
        assertEquals(node.get("nationalFormat").textValue(), TestConstants.PHONE.getNationalFormat());
        assertEquals(node.get("type").textValue(), "Phone");
        assertEquals(node.size(), 4);
        
        Phone deser = BridgeObjectMapper.get().readValue(node.toString(), Phone.class);
        assertEquals(deser.getNumber(), phone.getNumber());
        assertEquals(deser.getRegionCode(), phone.getRegionCode());
        assertEquals(deser.getNationalFormat(), TestConstants.PHONE.getNationalFormat());
    }
    
    @Test
    public void testToString() {
        assertEquals(TestConstants.PHONE.toString(), "Phone [regionCode=US, number=9712486796]");
    }
    
    @Test
    public void verifyPhoneFormatting() {
        // Forbidden planet, game store in London, in a local phone format.
        // Note that it's GB, not UK (!)
        Phone phone = new Phone("020-7420-3666", "GB");
        assertEquals(phone.getNumber(), "+442074203666");
        assertEquals(phone.getNationalFormat(), "020 7420 3666");
        assertEquals(phone.getRegionCode(), "GB");
    }
    
    @Test
    public void hibernateConstructionPathWorks() {
        Phone phone = new Phone();
        phone.setNumber(TestConstants.PHONE.getNationalFormat());
        phone.setRegionCode("US");
        assertEquals(phone.getNumber(), TestConstants.PHONE.getNumber());
        assertEquals(phone.getNationalFormat(), TestConstants.PHONE.getNationalFormat());
    }
    
    @Test
    public void invalidPhoneIsPreserved() {
        Phone phone = new Phone("999-999-9999", "US");
        assertEquals(phone.getNumber(), "999-999-9999");
        assertEquals(phone.getRegionCode(), "US");
        assertEquals(phone.getNationalFormat(), "999-999-9999");
        assertFalse(Phone.isValid(phone));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void phoneIsNull() {
        Phone.isValid(null);
    }

    @Test
    public void phoneIsValid() {
        assertTrue(Phone.isValid(new Phone("206.547.2600", "US")));
    }
    
    @Test
    public void phoneIsNotValidAsPhone() {
        assertFalse(Phone.isValid(new Phone("999-999-9999", "US")));
    }
    
    @Test
    public void phoneIsNotValidAsANumber() {
        assertFalse(Phone.isValid(new Phone("206-SPARKIES-DINER", "US")));
    }
    
    @Test
    public void phoneIsMissingRegionCode() {
        assertFalse(Phone.isValid(new Phone("206.547.2600", null)));
    }

    @Test
    public void phoneInvalidRegionCode() {
        assertFalse(Phone.isValid(new Phone("206.547.2600", "gibberish")));
    }

    @Test
    public void phoneInvalidRegionCodeInternationalFormat() {
        assertFalse(Phone.isValid(new Phone("+12065472600", "gibberish")));
    }

    @Test
    public void phoneIsMissingNumber() {
        assertFalse(Phone.isValid(new Phone(null, "US")));
    }
    
}
