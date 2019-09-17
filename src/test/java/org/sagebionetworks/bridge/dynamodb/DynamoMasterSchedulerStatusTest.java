package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerStatus;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoMasterSchedulerStatusTest {
    
    @Test
    public void canSerialize() throws Exception {
        MasterSchedulerStatus status = MasterSchedulerStatus.create();
        status.setHashKey(DynamoMasterSchedulerStatusDao.SCHEDULER_STATUS_HASH_KEY);
        status.setLastProcessedTime(2L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(status);
        assertEquals(node.get("hashKey").textValue(), DynamoMasterSchedulerStatusDao.SCHEDULER_STATUS_HASH_KEY);
        assertEquals(node.get("lastProcessedTime").longValue(), 2L);
        
        MasterSchedulerStatus deser = BridgeObjectMapper.get().treeToValue(node, MasterSchedulerStatus.class);
        assertEquals(deser.getHashKey(), status.getHashKey());
        assertEquals(deser.getLastProcessedTime(), status.getLastProcessedTime());
    }
}
