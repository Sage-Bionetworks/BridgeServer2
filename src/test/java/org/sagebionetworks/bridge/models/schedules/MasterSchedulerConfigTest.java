package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.TimestampHolder;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MasterSchedulerConfigTest {
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String CRON_SCHEDULE = "testCronSchedule";
    private static final String SQS_QUEUE_URL = "testSysQueueUrl";
    private static final long VERSION = 1L;
    
    @Test
    public void canSerialize() throws Exception {
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string");
        
        MasterSchedulerConfig holder = MasterSchedulerConfig.create();
        holder.setScheduleId(SCHEDULE_ID);
        holder.setCronSchedule(CRON_SCHEDULE);
        holder.setRequestTemplate(objNode);
        holder.setSqsQueueUrl(SQS_QUEUE_URL);
        holder.setVersion(VERSION);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(holder);
        
        assertEquals(SCHEDULE_ID, node.get("scheduleId").textValue());
        assertEquals(CRON_SCHEDULE, node.get("cronSchedule").textValue());
        assertTrue(node.get("requestTemplate").get("a").booleanValue());
        assertEquals("string", node.get("requestTemplate").get("b").textValue());
        assertEquals(SQS_QUEUE_URL, node.get("sqsQueueUrl").textValue());
        assertEquals(VERSION, node.get("version").asLong());
        assertEquals("MasterSchedulerConfig", node.get("type").textValue());
        
        MasterSchedulerConfig deser = BridgeObjectMapper.get().readValue(node.toString(), MasterSchedulerConfig.class);
        assertEquals(deser.getScheduleId(), SCHEDULE_ID);
        assertEquals(deser.getCronSchedule(), CRON_SCHEDULE);
        assertTrue(deser.getRequestTemplate().get("a").booleanValue());
        assertEquals(deser.getRequestTemplate().get("b").textValue(), "string");
        assertEquals(deser.getSqsQueueUrl(), SQS_QUEUE_URL);
        assertEquals(deser.getVersion(), new Long(VERSION));
    }
}
