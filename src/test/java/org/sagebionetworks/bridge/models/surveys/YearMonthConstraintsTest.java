package org.sagebionetworks.bridge.models.surveys;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.EnumSet;

import org.joda.time.YearMonth;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class YearMonthConstraintsTest {
    @Test
    public void canSerializeCorrectly() throws Exception {
        YearMonthConstraints constraints = new YearMonthConstraints();
        constraints.setEarliestValue(YearMonth.parse("2000-01"));
        constraints.setLatestValue(YearMonth.parse("2009-12"));
        constraints.setAllowFuture(true);
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);

        assertEquals(node.get("earliestValue").textValue(), "2000-01");
        assertEquals(node.get("latestValue").textValue(), "2009-12");
        assertEquals(node.get("dataType").textValue(), "yearmonth");
        assertTrue(node.get("allowFuture").booleanValue());
        assertEquals(node.get("type").textValue(), "YearMonthConstraints");
        
        // Deserialize as a Constraints object to verify the right subtype is selected.
        YearMonthConstraints deser = (YearMonthConstraints) BridgeObjectMapper.get()
                .readValue(node.toString(), Constraints.class);
        assertEquals(deser.getEarliestValue(), YearMonth.parse("2000-01"));
        assertEquals(deser.getLatestValue(), YearMonth.parse("2009-12"));
        assertTrue(deser.getAllowFuture());
        assertEquals(deser.getDataType(), DataType.YEARMONTH);
        assertEquals(deser.getSupportedHints(), EnumSet.of(UIHint.YEARMONTH));
    }
}
