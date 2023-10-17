package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StringMapConverterTest {
    private static final StringMapConverter CONVERTER = new StringMapConverter();

    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";
    private static final Map<String, String> MAP = ImmutableMap.of(TEST_KEY, TEST_VALUE);

    @Test
    public void convertToDatabaseColumn() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(MAP);
        Map<String, String> deser = BridgeObjectMapper.get().readValue(json, StringMapConverter.TYPE_REFERENCE);
        assertEquals(deser, MAP);
    }

    @Test
    public void convertToEntityAttribute() {
        ObjectNode json = BridgeObjectMapper.get().createObjectNode();
        json.put(TEST_KEY, TEST_VALUE);

        Map<String, String> map = CONVERTER.convertToEntityAttribute(json.toString());
        assertEquals(map, MAP);
    }
}
