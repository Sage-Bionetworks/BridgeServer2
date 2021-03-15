package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.TestConstants.COLOR_SCHEME;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ColorSchemeTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ColorScheme.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        JsonNode node = BridgeObjectMapper.get().valueToTree(COLOR_SCHEME);
        assertEquals(node.get("background").textValue(), "#000000");
        assertEquals(node.get("foreground").textValue(), "#FFFFFF");
        assertEquals(node.get("activated").textValue(), "#CCEECC");
        assertEquals(node.get("inactivated").textValue(), "#CCCCCC");
        
        ColorScheme retValue = BridgeObjectMapper.get().readValue(node.toString(), ColorScheme.class);        
        assertEquals(retValue.getBackground(), "#000000");
        assertEquals(retValue.getForeground(), "#FFFFFF");
        assertEquals(retValue.getActivated(), "#CCEECC");
        assertEquals(retValue.getInactivated(), "#CCCCCC");
    }
}
