package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DateTimeRangeResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        List<String> items = Lists.newArrayList("1", "2", "3");
        DateTimeRangeResourceList<String> list = new DateTimeRangeResourceList<>(items)
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime);
        
        assertEquals(list.getStartTime(), startTime);
        assertEquals(list.getEndTime(), endTime);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals(node.get("startTime").asText(), "2016-02-03T10:10:10.000-08:00");
        assertEquals(node.get("endTime").asText(), "2016-02-23T14:14:14.000-08:00");
        assertEquals(node.get("items").size(), 3);
        assertEquals(node.get("type").asText(), "DateTimeRangeResourceList");
        assertEquals(node.get("items").get(0).asText(), "1");
        assertEquals(node.get("items").get(1).asText(), "2");
        assertEquals(node.get("items").get(2).asText(), "3");
        assertEquals(node.size(), 6);
        assertEquals(node.get("requestParams").get("startTime").asText(), "2016-02-03T10:10:10.000-08:00");
        assertEquals(node.get("requestParams").get("endTime").asText(), "2016-02-23T14:14:14.000-08:00");
        assertEquals(node.get("requestParams").get(ResourceList.TYPE).asText(), ResourceList.REQUEST_PARAMS);
        
        // We never deserialize this on the server side (only in the SDK).
    }
    
    @Test
    public void canSerializeWithoutDeprecated() {
        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        List<String> items = Lists.newArrayList("1", "2", "3");
        DateTimeRangeResourceList<String> list = new DateTimeRangeResourceList<>(items, true)
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertNull(node.get("startTime"));
        assertNull(node.get("endTime"));
    }
}
