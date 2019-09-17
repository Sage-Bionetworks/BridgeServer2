package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DynamoMasterSchedulerConfigTest {
    
    private static final String SCHEDULE_ID = "test-schedule-id";
    private static final String CRON_SCHEDULE = "testCronSchedule";
    private static final String SQS_QUEUE_URL = "testSysQueueUrl";
    private static final DateTime startOfDay = DateTime.parse("2015-02-20T16:32:12.123-05:00");
    
    @Test
    public void canSerialize() throws Exception {
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string");
        objNode.put("c", startOfDay.toString());
        
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("a", "bodyString");
        objNode.put("d", body);
        
        MasterSchedulerConfig config = MasterSchedulerConfig.create();
        config.setScheduleId(SCHEDULE_ID);
        config.setCronSchedule(CRON_SCHEDULE);
        config.setRequestTemplate(objNode);
        config.setSqsQueueUrl(SQS_QUEUE_URL);
        config.setVersion(2L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(config);
        assertEquals(node.get("scheduleId").textValue(), SCHEDULE_ID);
        assertEquals(node.get("cronSchedule").textValue(), CRON_SCHEDULE);
        assertTrue(node.get("requestTemplate").get("a").booleanValue());
        assertEquals(node.get("requestTemplate").get("b").textValue(), "string");
        assertEquals(node.get("requestTemplate").get("c").textValue(), startOfDay.toString());
        assertEquals(node.get("requestTemplate").get("d").get("a").textValue(), "bodyString");
        assertEquals(node.get("sqsQueueUrl").textValue(), SQS_QUEUE_URL);
        assertEquals(node.get("version").longValue(), 2L);
        assertEquals(node.get("type").asText(), "MasterSchedulerConfig");
        
        MasterSchedulerConfig deser = BridgeObjectMapper.get().treeToValue(node, MasterSchedulerConfig.class);
        assertNotNull(deser.getScheduleId());
        assertEquals(deser.getScheduleId(), config.getScheduleId());
        assertEquals(deser.getCronSchedule(), config.getCronSchedule());
        assertEquals(deser.getRequestTemplate(), config.getRequestTemplate());
        assertEquals(deser.getSqsQueueUrl(), config.getSqsQueueUrl());
        assertEquals(deser.getVersion(), config.getVersion());
    }
}
