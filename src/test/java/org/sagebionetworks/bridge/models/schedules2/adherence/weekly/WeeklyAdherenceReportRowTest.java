package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class WeeklyAdherenceReportRowTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(WeeklyAdherenceReportRow.class)
            .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
            .verify();
    }
    
    @Test
    public void testPartiallyConstructedRowsAreEqual() {
        WeeklyAdherenceReportRow row1 = new WeeklyAdherenceReportRow();
        row1.setLabel("label");
        row1.setSearchableLabel("searchableLabel");
        row1.setSessionGuid("sessionGuid");
        row1.setStartEventId("eventId");
        row1.setSessionName("sessionName");
        row1.setSessionSymbol("sessionSymbol");
        row1.setWeekInStudy(2);
        
        WeeklyAdherenceReportRow row2 = new WeeklyAdherenceReportRow();
        row2.setLabel("label");
        row2.setSearchableLabel("searchableLabel");
        row2.setSessionGuid("sessionGuid");
        row2.setStartEventId("eventId");
        row2.setSessionName("sessionName");
        row2.setSessionSymbol("sessionSymbol");
        row2.setWeekInStudy(2);
        
        assertEquals(row1, row2);
    }
    
    @Test
    public void canSerialize() throws JsonMappingException, JsonProcessingException {
        WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow();
        row.setLabel("label");
        row.setSearchableLabel("searchableLabel");
        row.setSessionGuid("sessionGuid");
        row.setStartEventId("eventId");
        row.setSessionName("sessionName");
        row.setSessionSymbol("sessionSymbol");
        row.setWeekInStudy(2);
        row.setStudyBurstId("studyBurstId");
        row.setStudyBurstNum(4);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(row);
        assertEquals(node.get("label").textValue(), "label");
        assertEquals(node.get("searchableLabel").textValue(), "searchableLabel");
        assertEquals(node.get("sessionGuid").textValue(), "sessionGuid");
        assertEquals(node.get("startEventId").textValue(), "eventId");
        assertEquals(node.get("sessionName").textValue(), "sessionName");
        assertEquals(node.get("sessionSymbol").textValue(), "sessionSymbol");
        assertEquals(node.get("weekInStudy").intValue(), 2);
        assertEquals(node.get("studyBurstId").textValue(), "studyBurstId");
        assertEquals(node.get("studyBurstNum").intValue(), 4);
        assertEquals(node.get("type").textValue(), "WeeklyAdherenceReportRow");
        
        WeeklyAdherenceReportRow deser = BridgeObjectMapper.get().readValue(node.toString(), WeeklyAdherenceReportRow.class);
        assertEquals(deser, row);
    }
    
    @Test
    public void copy() {
        WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow();
        row.setLabel("label");
        row.setSearchableLabel("searchableLabel");
        row.setSessionGuid("sessionGuid");
        row.setStartEventId("eventId");
        row.setSessionName("sessionName");
        row.setSessionSymbol("sessionSymbol");
        row.setWeekInStudy(2);
        row.setStudyBurstId("studyBurstId");
        row.setStudyBurstNum(4);
        
        WeeklyAdherenceReportRow copy = row.copy();
        assertEquals(copy.getLabel(), row.getLabel());
        assertEquals(copy.getSearchableLabel(), row.getSearchableLabel());
        assertEquals(copy.getSessionGuid(), row.getSessionGuid());
        assertEquals(copy.getStartEventId(), row.getStartEventId());
        assertEquals(copy.getSessionName(), row.getSessionName());
        assertEquals(copy.getSessionSymbol(), row.getSessionSymbol());
        assertEquals(copy.getWeekInStudy(), row.getWeekInStudy());
        assertEquals(copy.getStudyBurstId(), row.getStudyBurstId());
        assertEquals(copy.getStudyBurstNum(), row.getStudyBurstNum());
    }

}
