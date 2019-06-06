package org.sagebionetworks.bridge.models.surveys;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class DateConstraintsTest {

    @Test
    public void canSerializeCorrectly() throws Exception {
        DateConstraints constraints = new DateConstraints();
        constraints.setEarliestValue(LocalDate.parse("2015-01-01"));
        constraints.setLatestValue(LocalDate.parse("2015-12-31"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("earliestValue").asText(), "2015-01-01");
        assertEquals(node.get("latestValue").asText(), "2015-12-31");
        assertEquals(node.get("dataType").asText(), "date");
        assertEquals(node.get("type").asText(), "DateConstraints");
    }
    
    
}
