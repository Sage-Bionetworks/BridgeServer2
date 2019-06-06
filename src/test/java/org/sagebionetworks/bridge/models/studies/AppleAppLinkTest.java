package org.sagebionetworks.bridge.models.studies;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AppleAppLinkTest {
    
    final static ObjectMapper MAPPER = new ObjectMapper();
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AppleAppLink.class).allFieldsShouldBeUsed().verify();
    }
    @Test
    public void canSerialize() throws Exception {
        AppleAppLink link = new AppleAppLink("appId", ImmutableList.of("/appId/", "/appId/*"));
        
        // We serialize this without the "type" attribute because we're following a schema handed
        // to us by Apple.
        JsonNode node = MAPPER.valueToTree(link);
        assertEquals(node.size(), 2);
        assertEquals(node.get("appID").textValue(), "appId");
        ArrayNode array = (ArrayNode)node.get("paths");
        assertEquals(array.size(), 2);
        assertEquals(array.get(0).textValue(), "/appId/");
        assertEquals(array.get(1).textValue(), "/appId/*");
        
        AppleAppLink deser = MAPPER.readValue(node.toString(), AppleAppLink.class);
        assertEquals(deser.getAppId(), "appId");
        assertEquals(deser.getPaths().get(0), "/appId/");
        assertEquals(deser.getPaths().get(1), "/appId/*");
    }
}
