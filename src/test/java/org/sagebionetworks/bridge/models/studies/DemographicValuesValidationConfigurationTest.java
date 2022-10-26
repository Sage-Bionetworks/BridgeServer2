package org.sagebionetworks.bridge.models.studies;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.validators.DemographicValuesValidator.DemographicValuesValidationType;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public class DemographicValuesValidationConfigurationTest {
    @Test
    public void serialize() throws JsonProcessingException {
        DemographicValuesValidationConfiguration config = new DemographicValuesValidationConfiguration();
        config.setValidationType(DemographicValuesValidationType.ENUM);
        JsonNode rules = BridgeObjectMapper.get().createObjectNode().set("en",
                BridgeObjectMapper.get().createArrayNode().add("foo").add("bar"));
        config.setValidationRules(rules);

        String serialized = BridgeObjectMapper.get().writeValueAsString(config);
        assertEquals(serialized,
                "{\"validationType\":\"enum\",\"validationRules\":{\"en\":[\"foo\",\"bar\"]},\"type\":\"DemographicValuesValidationConfiguration\"}");
    }

    @Test(expectedExceptions = JsonParseException.class)
    public void deserializeInvalid() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("foo", DemographicValuesValidationConfiguration.class);
    }

    @Test
    public void deserializeNull() throws JsonMappingException, JsonProcessingException {
        assertNull(BridgeObjectMapper.get().readValue("null", DemographicValuesValidationConfiguration.class));
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeWrongType() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("[]", DemographicValuesValidationConfiguration.class);
    }

    @Test
    public void deserializeNullType() throws JsonMappingException, JsonProcessingException {
        // will fail validation but can still be deserialized
        DemographicValuesValidationConfiguration result = BridgeObjectMapper.get().readValue(
                "{\"validationType\": null, \"validationRules\": {}}",
                DemographicValuesValidationConfiguration.class);
        assertNull(result.getValidationType());
    }

    @Test
    public void deserializeCaseInsensitiveType() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfiguration result1 = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"nUmBeR_rAnGe\", \"validationRules\": {}}",
                DemographicValuesValidationConfiguration.class);
        assertEquals(result1.getValidationType(), DemographicValuesValidationType.NUMBER_RANGE);
        DemographicValuesValidationConfiguration result2 = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"eNuM\", \"validationRules\": {}}",
                DemographicValuesValidationConfiguration.class);
        assertEquals(result2.getValidationType(), DemographicValuesValidationType.ENUM);
    }

    @Test
    public void deserializeMissingType() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfiguration result = BridgeObjectMapper.get()
                .readValue("{\"validationRules\": {}}", DemographicValuesValidationConfiguration.class);
        assertNull(result.getValidationType());
    }

    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeInvalidType() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"validationType\": \"foo\", \"validationRules\": {}}",
                DemographicValuesValidationConfiguration.class);
    }

    @Test
    public void deserializeNullRules() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfiguration result = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"NUMBER_RANGE\", \"validationRules\": null}",
                DemographicValuesValidationConfiguration.class);
        assertTrue(result.getValidationRules().isNull());
    }

    @Test
    public void deserializeMissingRules() throws JsonMappingException, JsonProcessingException {
        DemographicValuesValidationConfiguration result = BridgeObjectMapper.get().readValue(
                "{\"validationType\": \"NUMBER_RANGE\"}",
                DemographicValuesValidationConfiguration.class);
        assertNull(result.getValidationRules());
    }
}
