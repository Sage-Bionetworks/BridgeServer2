package org.sagebionetworks.bridge.models.schedules2;

import static nl.jqno.equalsverifier.Warning.NONFINAL_FIELDS;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.testng.Assert.assertEquals;

import org.joda.time.Period;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class StudyBurstTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(StudyBurst.class).suppress(NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("foo");
        burst.setOriginEventId("enrollment");
        burst.setDelay(Period.parse("P3W"));
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(FUTURE_ONLY);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(burst);
        assertEquals(node.size(), 7);
        assertEquals(node.get("identifier").textValue(), "foo");
        assertEquals(node.get("originEventId").textValue(), "enrollment");
        assertEquals(node.get("delay").textValue(), "P3W");
        assertEquals(node.get("interval").textValue(), "P1W");
        assertEquals(node.get("occurrences").intValue(), 3);
        assertEquals(node.get("updateType").textValue(), "future_only");
        assertEquals(node.get("type").textValue(), "StudyBurst");
        
        StudyBurst deser = BridgeObjectMapper.get().readValue(node.toString(), StudyBurst.class);
        assertEquals(deser, burst);
    }

}
