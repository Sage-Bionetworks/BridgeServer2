package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.persistence.PersistenceException;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;

public class Exporter3ConfigurationConverterTest {
    private static final Exporter3ConfigurationConverter CONVERTER = new Exporter3ConfigurationConverter();

    @Test
    public void convertToDatabaseColumn() throws Exception {
        Exporter3Configuration ex3Config = TestUtils.getValidExporter3Config();
        String json = CONVERTER.convertToDatabaseColumn(ex3Config);
        Exporter3Configuration deser = BridgeObjectMapper.get().readValue(json, Exporter3Configuration.class);

        assertEquals(deser, ex3Config);
    }

    // Nothing in the javadocs about whether null *will* be passed to this converter...
    @Test
    public void convertToDatabaseColumnNull() {
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }

    @Test
    public void convertToEntityAttribute() throws Exception {
        Exporter3Configuration ex3Config = TestUtils.getValidExporter3Config();
        String json = BridgeObjectMapper.get().writeValueAsString(ex3Config);

        Exporter3Configuration deser = CONVERTER.convertToEntityAttribute(json);
        assertEquals(deser, ex3Config);
    }

    @Test
    public void convertToEntityAttributeNull() {
        Exporter3Configuration deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void convertToEntityAttributeJsonErrorThrows() {
        CONVERTER.convertToEntityAttribute("not json");
    }
}
