package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.DemographicValue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

public class DemographicValueDeserializer extends JsonDeserializer<DemographicValue> {
    public DemographicValueDeserializer() {}

    @Override
    public DemographicValue deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        JsonNode node = p.readValueAsTree();
        DemographicValue value = new DemographicValue();
        if (node.isObject()) {
            if (node.has("value")) {
                if (node.get("value").isContainerNode()) {
                    throw new JsonMappingException(p, "value cannot be a container");
                }
                value.setValue(node.get("value").asText());
            }
            if (node.has("invalidity")) {
                value.setInvalidity(node.get("invalidity").asText());
            }
        } else if (node.isArray()) {
            throw new JsonMappingException(p, "value cannot be an array");
        } else {
            value.setValue(node.asText());
        }
        return value;
    }
}
