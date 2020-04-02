package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DynamoReportDataTest {
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final DateTime DATETIME = DateTime.parse("2015-02-20T16:32:12.123-05:00");

    @Test
    public void canSerialize() throws Exception {
        DynamoReportData reportData = new DynamoReportData();
        
        ReportDataKey key = new ReportDataKey.Builder().withHealthCode("ABC")
                .withIdentifier("foo").withReportType(ReportType.PARTICIPANT)
                .withStudyIdentifier(TEST_STUDY_IDENTIFIER).build();
        
        ObjectNode objNode = JsonNodeFactory.instance.objectNode();
        objNode.put("a", true);
        objNode.put("b", "string");
        objNode.put("c", 10);
        
        reportData.setKey(key.getKeyString());
        reportData.setDateTime(DATETIME);
        reportData.setData(objNode);
        reportData.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        
        String json = MAPPER.writeValueAsString(reportData);
        
        JsonNode node = MAPPER.readTree(json);
        assertNull(node.get("key"));
        assertEquals(node.get("date").textValue(), DATETIME.toString());
        assertEquals(node.get("dateTime").textValue(), DATETIME.toString());
        assertTrue(node.get("data").get("a").booleanValue());
        assertEquals(node.get("data").get("b").textValue(), "string");
        assertEquals(node.get("data").get("c").intValue(), 10);
        assertEquals(node.get("substudyIds").get(0).textValue(), "substudyA");
        assertEquals(node.get("substudyIds").get(1).textValue(), "substudyB");
        assertEquals(node.get("type").textValue(), "ReportData");
        assertEquals(node.size(), 5);
        
        ReportData deser = MAPPER.readValue(json, ReportData.class);
        assertNull(deser.getKey());
        assertEquals(deser.getDateTime(), DATETIME);
        assertEquals(deser.getDate(), DATETIME.toString());
        assertTrue(deser.getData().get("a").asBoolean());
        assertEquals(deser.getData().get("b").asText(), "string");
        assertEquals(deser.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        assertEquals(deser.getData().get("c").asInt(), 10);
    }
    
    @Test
    public void canSetEitherLocalDateOrDateTime() throws Exception {
        DateTime dateTime = DateTime.parse("2016-10-10T10:42:42.123-07:00");
        LocalDate localDate = LocalDate.parse("2016-10-10");
        
        DynamoReportData report = new DynamoReportData();
        report.setLocalDate(localDate);
        assertEquals(report.getDate(), localDate.toString());
        assertEquals(report.getLocalDate(), localDate);
        assertNull(report.getDateTime());
        
        report = new DynamoReportData();
        report.setDateTime(dateTime);
        assertEquals(report.getDate(), dateTime.toString());
        assertEquals(report.getDateTime(), dateTime);
        assertNull(report.getLocalDate());
    }
}
