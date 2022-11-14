package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class LabelConverterTest {
    @Test
    public void convertToDatabaseColumn() throws JsonProcessingException {
        LabelConverter converter = new LabelConverter();

        for (Label label : LABELS) {
            String retValue = converter.convertToDatabaseColumn(label);
            assertEquals(retValue, BridgeObjectMapper.get().writeValueAsString(label));
        }
    }

    @Test
    public void convertToEntityAttribute() throws JsonProcessingException {
        LabelConverter converter = new LabelConverter();

        for (Label label : LABELS) {
            Label retValue = converter.convertToEntityAttribute(BridgeObjectMapper.get().writeValueAsString(label));
            assertNotNull(retValue);
            assertNotNull(retValue.getLang());
            assertNotNull(retValue.getValue());
        }
    }

    @Test
    public void handlesNulls() {
        LabelConverter converter = new LabelConverter();
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
