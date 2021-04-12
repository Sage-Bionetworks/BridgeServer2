package org.sagebionetworks.bridge.models.studies;

import static org.sagebionetworks.bridge.TestUtils.getAddress;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AddressTest extends Mockito {
    @Test
    public void canSerialize() throws Exception {
        JsonNode node = BridgeObjectMapper.get().valueToTree(getAddress());
        assertEquals(node.size(), 8);
        assertEquals(node.get("placeName").textValue(), "aPlaceName");
        assertEquals(node.get("street").textValue(), "aStreet");
        assertEquals(node.get("mailRouting").textValue(), "aMailRouting");
        assertEquals(node.get("city").textValue(), "aCity");
        assertEquals(node.get("division").textValue(), "aState");
        assertEquals(node.get("postalCode").textValue(), "aPostalCode");
        assertEquals(node.get("country").textValue(), "aCountry");
        assertEquals(node.get("type").textValue(), "Address");
        
        Address deser = BridgeObjectMapper.get().readValue(node.toString(), Address.class);
        assertEquals(deser.getPlaceName(), "aPlaceName");
        assertEquals(deser.getStreet(), "aStreet");
        assertEquals(deser.getMailRouting(), "aMailRouting");
        assertEquals(deser.getCity(), "aCity");
        assertEquals(deser.getDivision(), "aState");
        assertEquals(deser.getPostalCode(), "aPostalCode");
        assertEquals(deser.getCountry(), "aCountry");
    }
}
