package org.sagebionetworks.bridge.models.schedules;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConfigReferenceTest {

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(ConfigReference.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void normalCase() {
        ConfigReference configRef = new ConfigReference("config1", 7L);
        assertEquals(configRef.getId(), "config1");
        assertEquals(configRef.getRevision().intValue(), 7);
    }

    @Test
    public void nullProps() {
        ConfigReference configRef = new ConfigReference(null, null);
        assertNull(configRef.getId());
        assertNull(configRef.getRevision());
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'id':'test-config', 'revision':7}");

        // convert to POJO
        ConfigReference configRef = BridgeObjectMapper.get().readValue(jsonText, ConfigReference.class);
        assertEquals(configRef.getId(), "test-config");
        assertEquals(configRef.getRevision().longValue(), 7L);

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(configRef, JsonNode.class);
        assertEquals(jsonNode.size(), 3);
        assertEquals(jsonNode.get("id").textValue(), "test-config");
        assertEquals(jsonNode.get("revision").longValue(), 7);
        assertEquals(jsonNode.get("type").textValue(), "ConfigReference");
    }
}
