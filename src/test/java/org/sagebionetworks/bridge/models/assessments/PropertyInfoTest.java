package org.sagebionetworks.bridge.models.assessments;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PropertyInfoTest {
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(PropertyInfo.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canRoundtripSerialize() throws Exception { 
        PropertyInfo info = new PropertyInfo.Builder()
                .withPropName("propName")
                .withLabel("propName label")
                .withDescription("propName description")
                .withPropType("string").build();

        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("propName").textValue(), "propName");
        assertEquals(node.get("label").textValue(), "propName label");
        assertEquals(node.get("description").textValue(), "propName description");
        assertEquals(node.get("propType").textValue(), "string");
        assertEquals(node.get("type").textValue(), "PropertyInfo");
        
        PropertyInfo deser = BridgeObjectMapper.get().readValue(node.toString(), PropertyInfo.class);
        assertEquals(deser, info);
    }
}
