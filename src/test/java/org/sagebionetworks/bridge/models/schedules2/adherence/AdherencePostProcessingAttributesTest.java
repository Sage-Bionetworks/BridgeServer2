package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AdherencePostProcessingAttributesTest {
    @Test
    public void deserialize() throws Exception {
        // We read these from JSON, but we never write them to JSON, so we don't need to test serialization.
        String jsonText = "{" +
                "   \"postProcessingAttributes\":{" +
                "       \"foo\":\"bar\"," +
                "       \"baz\":42" +
                "   }," +
                "   \"postProcessingCompletedOn\":\"2018-08-08T12:34:56.789Z\"," +
                "   \"postProcessingStatus\":\"status\"," +
                "   \"startedOn\":\"2018-08-06T22:59:27.308Z\"" +
                "}";

        // Deserialize.
        AdherencePostProcessingAttributes attributes = BridgeObjectMapper.get().readValue(jsonText,
                AdherencePostProcessingAttributes.class);
        assertEquals(attributes.getPostProcessingCompletedOn().toString(), "2018-08-08T12:34:56.789Z");
        assertEquals(attributes.getPostProcessingStatus(), "status");
        assertEquals(attributes.getStartedOn().toString(), "2018-08-06T22:59:27.308Z");

        JsonNode attributesNode = attributes.getPostProcessingAttributes();
        assertEquals(attributesNode.size(), 2);
        assertEquals(attributesNode.get("foo").textValue(), "bar");
        assertEquals(attributesNode.get("baz").intValue(), 42);
    }
}
