package org.sagebionetworks.bridge.models.studies;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.validators.DemographicValuesValidationType;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public class DemographicValuesValidationConfigTest {
    @Test
    public void serializeEnum() throws JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        JsonNode rules = BridgeObjectMapper.get().createObjectNode().set("en",
                BridgeObjectMapper.get().createArrayNode().add("foo").add("bar"));
        config.setValidationRules(rules);

        String serialized = BridgeObjectMapper.get().writeValueAsString(config);
        assertEquals(serialized,
                "{\"validationType\":\"enum\",\"validationRules\":{\"en\":[\"foo\",\"bar\"]},\"type\":\"DemographicValuesValidationConfig\"}");
    }

    @Test
    public void serializeNumberRange() throws JsonProcessingException {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        config.setValidationType(DemographicValuesValidationType.NUMBER_RANGE);
        JsonNode rules = BridgeObjectMapper.get().createObjectNode().put("min", 5).put("max", 10d);
        config.setValidationRules(rules);

        String serialized = BridgeObjectMapper.get().writeValueAsString(config);
        assertEquals(serialized,
                "{\"validationType\":\"number_range\",\"validationRules\":{\"min\":5,\"max\":10.0},\"type\":\"DemographicValuesValidationConfig\"}");
    }

    @Test
    public void deserializeValidEnum() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig deserialized = BridgeObjectMapper.get().readValue(
                "{\"validationType\":\"enum\",\"validationRules\":{\"en\":[\"foo\",\"bar\"]},\"type\":\"DemographicValuesValidationConfiguration\"}",
                DemographicValuesValidationConfig.class);
        assertEquals(deserialized.getValidationType(), DemographicValuesValidationType.ENUM);
        assertTrue(deserialized.getValidationRules().isObject());
        assertEquals(deserialized.getValidationRules().size(), 1);
        assertTrue(deserialized.getValidationRules().has("en"));
        assertTrue(deserialized.getValidationRules().get("en").isArray());
        assertEquals(deserialized.getValidationRules().get("en").size(), 2);
        assertEquals(deserialized.getValidationRules().get("en").get(0).asText(), "foo");
        assertEquals(deserialized.getValidationRules().get("en").get(1).asText(), "bar");
    }

    @Test
    public void deserializeValidNumberRange() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig deserialized = BridgeObjectMapper.get().readValue(
                "{\"validationType\":\"number_range\",\"validationRules\":{\"min\":5,\"max\":10.0},\"type\":\"DemographicValuesValidationConfiguration\"}",
                DemographicValuesValidationConfig.class);
        assertEquals(deserialized.getValidationType(), DemographicValuesValidationType.NUMBER_RANGE);
        assertTrue(deserialized.getValidationRules().isObject());
        assertEquals(deserialized.getValidationRules().size(), 2);
        assertTrue(deserialized.getValidationRules().has("min"));
        assertTrue(deserialized.getValidationRules().get("min").isNumber());
        assertEquals(deserialized.getValidationRules().get("min").asDouble(), 5d);
        assertTrue(deserialized.getValidationRules().has("max"));
        assertTrue(deserialized.getValidationRules().get("max").isNumber());
        assertEquals(deserialized.getValidationRules().get("max").asDouble(), 10d);
    }

    @Test(expectedExceptions = JsonParseException.class)
    public void deserializeInvalid() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("foo", DemographicValuesValidationConfig.class);
    }

    @Test
    public void deserializeNull() throws JsonMappingException, JsonProcessingException {
        assertNull(BridgeObjectMapper.get().readValue("null", DemographicValuesValidationConfig.class));
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeWrongType() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("[]", DemographicValuesValidationConfig.class);
    }

    @Test
    public void deserializeNullType() throws JsonMappingException, JsonProcessingException {
        // will fail validation but can still be deserialized
        DemographicValuesValidationConfig result = BridgeObjectMapper.get().readValue(
                "{\"validationType\": null, \"validationRules\": {}}",
                DemographicValuesValidationConfig.class);
        assertNull(result.getValidationType());
    }

    @Test
    public void deserializeCaseInsensitiveType() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig result1 = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"nUmBeR_rAnGe\", \"validationRules\": {}}",
                DemographicValuesValidationConfig.class);
        assertEquals(result1.getValidationType(), DemographicValuesValidationType.NUMBER_RANGE);
        DemographicValuesValidationConfig result2 = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"eNuM\", \"validationRules\": {}}",
                DemographicValuesValidationConfig.class);
        assertEquals(result2.getValidationType(), DemographicValuesValidationType.ENUM);
    }

    @Test
    public void deserializeMissingType() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig result = BridgeObjectMapper.get()
                .readValue("{\"validationRules\": {}}", DemographicValuesValidationConfig.class);
        assertNull(result.getValidationType());
    }

    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeInvalidType() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"validationType\": \"foo\", \"validationRules\": {}}",
                DemographicValuesValidationConfig.class);
    }

    @Test
    public void deserializeNullRules() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig result = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"NUMBER_RANGE\", \"validationRules\": null}",
                DemographicValuesValidationConfig.class);
        assertTrue(result.getValidationRules().isNull());
    }

    @Test
    public void deserializeMissingRules() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfig result = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"NUMBER_RANGE\"}",
                DemographicValuesValidationConfig.class);
        assertNull(result.getValidationRules());
    }
}
