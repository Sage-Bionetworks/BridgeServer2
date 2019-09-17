package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertEquals;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class MasterSchedulerStatusTest {
    private static final String HASH_KEY = "hashkey";
    private static final long LAST_PROCESS_TIME_MILLIS = DateTime.parse("2018-03-27T18:30-07:00").getMillis();
    
    @Test
    public void canSerialize() throws Exception {
        MasterSchedulerStatus holder = MasterSchedulerStatus.create();
        holder.setHashKey(HASH_KEY);
        holder.setLastProcessedTime(LAST_PROCESS_TIME_MILLIS);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(holder);
        
        assertEquals(HASH_KEY, node.get("hashKey").textValue());
        assertEquals(LAST_PROCESS_TIME_MILLIS, node.get("lastProcessedTime").asLong());
        assertEquals("MasterSchedulerStatus", node.get("type").textValue());
        
        MasterSchedulerStatus deser = BridgeObjectMapper.get().readValue(node.toString(), MasterSchedulerStatus.class);
        assertEquals(deser.getHashKey(), HASH_KEY);
        assertEquals(deser.getLastProcessedTime(), new Long(LAST_PROCESS_TIME_MILLIS));
    }
}
