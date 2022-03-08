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
        // We don't use session instance GUID because we're trying to find the rows
        // that are the same across the full report, and we don't want new rows for 
        // multiple session instances in the one week.
        EqualsVerifier.forClass(WeeklyAdherenceReportRow.class)
            .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
            .verify();
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

}
