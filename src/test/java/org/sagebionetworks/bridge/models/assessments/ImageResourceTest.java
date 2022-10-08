package org.sagebionetworks.bridge.models.assessments;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

public class ImageResourceTest {
    @Test
    public void canSerialize() throws JsonMappingException, JsonProcessingException {
        ImageResource imageResource = new ImageResource();
        imageResource.setName("default");
        imageResource.setModule("sage_survey");
        Label label = new Label("en", "english label");
        imageResource.setLabel(label);

        JsonNode node = BridgeObjectMapper.get().valueToTree(imageResource);

        assertEquals(node.get("name").textValue(), "default");
        assertEquals(node.get("module").textValue(), "sage_survey");
        assertEquals(node.get("label").get("lang").textValue(), "en");
        assertEquals(node.get("label").get("value").textValue(), "english label");

        ImageResource deserialized = BridgeObjectMapper.get().readValue(node.toString(), ImageResource.class);

        assertEquals(deserialized.getName(), "default");
        assertEquals(deserialized.getModule(), "sage_survey");
        assertEquals(deserialized.getLabel().getLang(), "en");
        assertEquals(deserialized.getLabel().getValue(), "english label");
    }

    @Test
    public void serializeHandlesNulls() {
        ImageResource imageResource = new ImageResource();
        JsonNode node = BridgeObjectMapper.get().valueToTree(imageResource);
        assertEquals(node.size(), 1);
        assertEquals(node.get("type").textValue(), "ImageResource");
    }
}
