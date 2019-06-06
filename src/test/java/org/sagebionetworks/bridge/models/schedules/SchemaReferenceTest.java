package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class SchemaReferenceTest {
    @Test
    public void normalCase() {
        SchemaReference schemaRef = new SchemaReference("test-schema", 7);
        assertEquals(schemaRef.getId(), "test-schema");
        assertEquals(schemaRef.getRevision().intValue(), 7);
        assertEquals(schemaRef.toString(), "SchemaReference{id='test-schema', revision=7}");
    }

    @Test
    public void nullProps() {
        // This is technically invalid, but this is validated by the validator. We need to make sure nothing crashes if
        // everything is null.
        SchemaReference schemaRef = new SchemaReference(null, null);
        assertNull(schemaRef.getId());
        assertNull(schemaRef.getRevision());
        assertNotNull(schemaRef.toString());
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"test-schema\",\n" +
                "   \"revision\":7\n" +
                "}";

        // convert to POJO
        SchemaReference schemaRef = BridgeObjectMapper.get().readValue(jsonText, SchemaReference.class);
        assertEquals(schemaRef.getId(), "test-schema");
        assertEquals(schemaRef.getRevision().intValue(), 7);

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(schemaRef, JsonNode.class);
        assertEquals(jsonNode.size(), 3);
        assertEquals(jsonNode.get("id").textValue(), "test-schema");
        assertEquals(jsonNode.get("revision").intValue(), 7);
        assertEquals(jsonNode.get("type").textValue(), "SchemaReference");
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(SchemaReference.class).allFieldsShouldBeUsed().verify();
    }
}
