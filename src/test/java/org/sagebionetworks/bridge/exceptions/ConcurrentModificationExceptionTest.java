package org.sagebionetworks.bridge.exceptions;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class ConcurrentModificationExceptionTest {
    private static final TestEntity DUMMY_ENTITY = new TestEntity();
    private static final String ERROR_MESSAGE = "Something bad happened.";

    @Test
    public void serialization() {
        ConcurrentModificationException ex = new ConcurrentModificationException(DUMMY_ENTITY, ERROR_MESSAGE);
        JsonNode exNode = BridgeObjectMapper.get().valueToTree(ex);
        assertTrue(exNode.has("entity"));
        assertTrue(exNode.has("entityClass"));
        assertEquals(exNode.get("message").textValue(), ERROR_MESSAGE);
        assertEquals(exNode.get("statusCode").intValue(), SC_CONFLICT);
    }

    @Test
    public void serializationNullEntity() {
        ConcurrentModificationException ex = new ConcurrentModificationException(ERROR_MESSAGE);
        JsonNode exNode = BridgeObjectMapper.get().valueToTree(ex);
        assertFalse(exNode.has("entity"));
        assertFalse(exNode.has("entityClass"));
        assertEquals(exNode.get("message").textValue(), ERROR_MESSAGE);
        assertEquals(exNode.get("statusCode").intValue(), SC_CONFLICT);
    }

    // Trivial class with a single getter. We need a concrete implementation of BridgeEntity, and Jackson requires that
    // it be non-empty.
    private static class TestEntity implements BridgeEntity {
        @SuppressWarnings("unused")
        public String getFoo() {
            return "foo";
        }
    }
}
