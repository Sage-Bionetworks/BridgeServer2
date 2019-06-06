package org.sagebionetworks.bridge.models;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class ReportTypeResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        ReportIndex index1 = ReportIndex.create();
        index1.setKey("doesn't matter what this is");
        index1.setIdentifier("foo");
        
        ReportIndex index2 = ReportIndex.create();
        index2.setKey("doesn't matter what this is");
        index2.setIdentifier("bar");
        index2.setPublic(true);
        
        ReportTypeResourceList<ReportIndex> list = new ReportTypeResourceList<>(
                Lists.newArrayList(index1, index2)).withRequestParam(ResourceList.REPORT_TYPE, ReportType.PARTICIPANT);
        
        assertEquals(list.getReportType(), ReportType.PARTICIPANT);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals(node.get("reportType").asText(), "participant");
        assertEquals(node.get("items").size(), 2);
        assertEquals(node.get("requestParams").get("reportType").asText(), "participant");
        assertEquals(node.get("requestParams").get(ResourceList.TYPE).textValue(), ResourceList.REQUEST_PARAMS);
        assertEquals(node.get("type").asText(), "ReportTypeResourceList");
        assertEquals(node.get("items").get(0).get("identifier").asText(), "foo");
        assertFalse(node.get("items").get(0).get("public").asBoolean());
        assertEquals(node.get("items").get(1).get("identifier").asText(), "bar");
        assertTrue(node.get("items").get(1).get("public").asBoolean());
        assertEquals(node.size(), 5);
        
        // We never deserialize this on the server side (only in the SDK).
    }
}
