package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.TestConstants.LABELS;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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
        imageResource.setLabels(LABELS);

        JsonNode node = BridgeObjectMapper.get().valueToTree(imageResource);

        assertEquals(node.size(), 4);
        assertEquals(node.get("name").textValue(), "default");
        assertEquals(node.get("module").textValue(), "sage_survey");
        assertEquals(node.get("labels").get(0).get("lang").textValue(), LABELS.get(0).getLang());
        assertEquals(node.get("labels").get(0).get("value").textValue(), LABELS.get(0).getValue());
        assertEquals(node.get("labels").get(1).get("lang").textValue(), LABELS.get(1).getLang());
        assertEquals(node.get("labels").get(1).get("value").textValue(), LABELS.get(1).getValue());

        ImageResource deserialized = BridgeObjectMapper.get().readValue(node.toString(), ImageResource.class);

        assertEquals(deserialized.getName(), "default");
        assertEquals(deserialized.getModule(), "sage_survey");
        assertEquals(deserialized.getLabels().get(0).getLang(), LABELS.get(0).getLang());
        assertEquals(deserialized.getLabels().get(0).getValue(), LABELS.get(0).getValue());
        assertEquals(deserialized.getLabels().get(1).getLang(), LABELS.get(1).getLang());
        assertEquals(deserialized.getLabels().get(1).getValue(), LABELS.get(1).getValue());
    }

    @Test
    public void serializeHandlesNulls() {
        ImageResource imageResource = new ImageResource();
        JsonNode node = BridgeObjectMapper.get().valueToTree(imageResource);
        assertEquals(node.size(), 1);
        assertEquals(node.get("type").textValue(), "ImageResource");
    }
}
