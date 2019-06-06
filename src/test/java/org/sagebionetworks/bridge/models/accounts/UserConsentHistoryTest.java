package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class UserConsentHistoryTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(UserConsentHistory.class).allFieldsShouldBeUsed().verify();
    }
    
    // We do not currently expose this as JSON (in fact we never return a healthCode through the API), 
    // but it's possible we will, so verify this works.
    @Test
    public void serialization() throws Exception {
        long consentCreatedOn = 1446136164293L;
        long signedOn = 1446136182504L;
        long withdrewOn = 1446136196168L;
        
        UserConsentHistory history = new UserConsentHistory.Builder()
           .withHealthCode("AAA")
           .withSubpopulationGuid(SubpopulationGuid.create("BBB"))
           .withConsentCreatedOn(consentCreatedOn)
           .withName("CCC")
           .withBirthdate("1980-04-02")
           .withImageData("imageData")
           .withImageMimeType("image/png")
           .withSignedOn(signedOn)
           .withWithdrewOn(withdrewOn)
           .withHasSignedActiveConsent(true).build();
       
        // Do not print the healthCode in any loging that is done.
        assertTrue(history.toString().contains("healthCode=[REDACTED]"));
        assertTrue(history.toString().contains("name=[REDACTED]"));
        assertTrue(history.toString().contains("birthdate=[REDACTED]"));
        assertTrue(history.toString().contains("imageData=[REDACTED]"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(history);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertNull(node.get("healthCode"));
        assertEquals(node.get("subpopulationGuid").asText(), "BBB");
        assertEquals(node.get("consentCreatedOn").asText(), "2015-10-29T16:29:24.293Z");
        assertEquals(node.get("imageMimeType").asText(), "image/png");
        assertEquals(node.get("signedOn").asText(), "2015-10-29T16:29:42.504Z");
        assertEquals(node.get("withdrewOn").asText(), "2015-10-29T16:29:56.168Z");
        assertEquals(node.get("hasSignedActiveConsent").asBoolean(), true);
        assertEquals(node.get("type").asText(), "UserConsentHistory");
        
        // This has to be added for the deserialized version to be equal to original
        ((ObjectNode)node).put("healthCode", "AAA");
        
        UserConsentHistory newHistory = BridgeObjectMapper.get().readValue(node.toString(), UserConsentHistory.class);
        assertEquals(newHistory, history);
    }
}
