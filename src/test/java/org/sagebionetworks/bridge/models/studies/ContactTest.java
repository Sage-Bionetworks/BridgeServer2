package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestUtils.getAddress;
import static org.sagebionetworks.bridge.models.studies.ContactRole.PRINCIPAL_INVESTIGATOR;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ContactTest extends Mockito {
    
    // Not sure if we can implement equals/hashCode for this class, I'll test.
    
    @Test
    public void canSerialize() throws Exception {
        Contact contact = new Contact();
        contact.setRole(PRINCIPAL_INVESTIGATOR);
        contact.setName("Tim Powers, Ph.D.");
        contact.setPosition("Associate Professor");
        contact.setAffiliation("Miskatonic University");
        contact.setAddress(getAddress());
        contact.setEmail(EMAIL);
        contact.setPhone(PHONE);
        contact.setJurisdiction("US");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(contact);
        assertEquals(node.get("role").textValue(), "principal_investigator");
        assertEquals(node.get("name").textValue(), "Tim Powers, Ph.D.");
        assertEquals(node.get("position").textValue(), "Associate Professor");
        assertEquals(node.get("affiliation").textValue(), "Miskatonic University");
        // This is tested separately, just verify it is there
        assertEquals(node.get("address").get("street").textValue(), "aStreet");
        assertEquals(node.get("email").textValue(), EMAIL);
        assertEquals(node.get("phone").get("number").textValue(), PHONE.getNumber());
        assertEquals(node.get("jurisdiction").textValue(), "US");
        assertEquals(node.get("type").textValue(), "Contact");
        
        Contact deser = BridgeObjectMapper.get().readValue(node.toString(), Contact.class);
        assertEquals(deser.getRole(), PRINCIPAL_INVESTIGATOR);
        assertEquals(deser.getName(), "Tim Powers, Ph.D.");
        assertEquals(deser.getPosition(), "Associate Professor");
        assertEquals(deser.getAffiliation(), "Miskatonic University");
        
        Address existing = TestUtils.getAddress();
        assertEquals(deser.getAddress().getPlaceName(), existing.getPlaceName());
        assertEquals(deser.getAddress().getStreet(), existing.getStreet());
        assertEquals(deser.getAddress().getMailRouting(), existing.getMailRouting());
        assertEquals(deser.getAddress().getCity(), existing.getCity());
        assertEquals(deser.getAddress().getDivision(), existing.getDivision());
        assertEquals(deser.getAddress().getPostalCode(), existing.getPostalCode());
        assertEquals(deser.getAddress().getCountry(), existing.getCountry());
        
        assertEquals(deser.getEmail(), EMAIL);
        assertEquals(deser.getPhone(), PHONE);
        assertEquals(deser.getJurisdiction(), "US");
    }
}
