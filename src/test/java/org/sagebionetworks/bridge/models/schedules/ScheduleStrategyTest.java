package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ScheduleStrategyTest {

    @Test
    public void verifyStrategyDoesNotSerializeGetAllPossibleSchedules() throws Exception {
        ScheduleStrategy strategy = TestUtils.getABTestSchedulePlan(API_APP_ID).getStrategy();
        
        String output = BridgeObjectMapper.get().writeValueAsString(strategy);
        JsonNode node = BridgeObjectMapper.get().readTree(output);
        assertNull(node.get("allPossibleSchedules"));
        assertEquals(node.get("type").asText(), "ABTestScheduleStrategy");
        assertEquals(((ArrayNode)node.get("scheduleGroups")).size(), 3);
    }
    
    @Test
    public void verifyDeserializationOfStrategies() throws Exception {
        ScheduleStrategy strategy = new ABTestScheduleStrategy();
        ScheduleStrategy reconstitutedStrategy = serializeAndDeserialize(strategy, "ABTestScheduleStrategy");
        assertTrue(reconstitutedStrategy instanceof ABTestScheduleStrategy);
        
        strategy = new SimpleScheduleStrategy();
        reconstitutedStrategy = serializeAndDeserialize(strategy, "SimpleScheduleStrategy");
        assertTrue(reconstitutedStrategy instanceof SimpleScheduleStrategy);
        
        strategy = new CriteriaScheduleStrategy();
        reconstitutedStrategy = serializeAndDeserialize(strategy, "CriteriaScheduleStrategy");
        assertTrue(reconstitutedStrategy instanceof CriteriaScheduleStrategy);
    }
    
    private ScheduleStrategy serializeAndDeserialize(ScheduleStrategy strategy, String typeName) throws Exception {
        String output = BridgeObjectMapper.get().writeValueAsString(strategy);
        JsonNode node = BridgeObjectMapper.get().readTree(output);
        assertEquals(node.get("type").asText(), typeName);
        return BridgeObjectMapper.get().readValue(output, ScheduleStrategy.class);
    }

}
