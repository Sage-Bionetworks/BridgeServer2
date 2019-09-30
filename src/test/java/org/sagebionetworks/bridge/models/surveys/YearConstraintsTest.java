package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.models.surveys.UIHint.NUMBERFIELD;
import static org.sagebionetworks.bridge.models.surveys.UIHint.SLIDER;
import static org.sagebionetworks.bridge.models.surveys.UIHint.YEAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.EnumSet;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class YearConstraintsTest {
    @Test
    public void canSerializeCorrectly() throws Exception {
        YearConstraints constraints = new YearConstraints();
        constraints.setEarliestValue("2000");
        constraints.setLatestValue("2009");
        constraints.setAllowFuture(true);
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals(node.get("earliestValue").textValue(), "2000");
        assertEquals(node.get("latestValue").textValue(), "2009");
        assertEquals(node.get("dataType").textValue(), "year");
        assertTrue(node.get("allowFuture").booleanValue());
        // allowPast is true by default
        assertTrue(node.get("allowPast").booleanValue());
        assertEquals(node.get("type").textValue(), "YearConstraints");
        
        // but allowPast can be disabled
        constraints.setAllowPast(false);
        node = BridgeObjectMapper.get().valueToTree(constraints);
        assertFalse(node.get("allowPast").booleanValue());
        
        // Deserialize as a Constraints object to verify the right subtype is selected.
        YearConstraints deser = (YearConstraints) BridgeObjectMapper.get()
                .readValue(node.toString(), Constraints.class);
        assertEquals(deser.getEarliestValue(), "2000");
        assertEquals(deser.getLatestValue(), "2009");
        assertTrue(deser.getAllowFuture());
        assertEquals(deser.getDataType(), DataType.YEAR);
        assertEquals(deser.getSupportedHints(), EnumSet.of(YEAR, NUMBERFIELD, SLIDER));
    }
}
