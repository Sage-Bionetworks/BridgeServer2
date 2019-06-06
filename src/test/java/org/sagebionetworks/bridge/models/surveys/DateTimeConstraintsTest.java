package org.sagebionetworks.bridge.models.surveys;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class DateTimeConstraintsTest {

    @Test
    public void canSerializeCorrectly() throws Exception {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setEarliestValue(DateTime.parse("2015-01-01T10:10:10-07:00"));
        constraints.setLatestValue(DateTime.parse("2015-12-31T10:10:10-07:00"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        // The serialization is losing the time zone used by the user initially,
        // and this we don't want. We would need to create custom serializer/deserializers
        // for this.
        assertEquals(node.get("earliestValue").asText(), "2015-01-01T10:10:10.000-07:00");
        assertEquals(node.get("latestValue").asText(), "2015-12-31T10:10:10.000-07:00");
        assertEquals(node.get("dataType").asText(), "datetime");
        assertEquals(node.get("type").asText(), "DateTimeConstraints");
    }
    
}
