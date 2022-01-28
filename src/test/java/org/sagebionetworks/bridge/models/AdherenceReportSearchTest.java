package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.models.AccountTestFilter.BOTH;
import static org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantProgressionState.DONE;
import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AdherenceReportSearchTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AdherenceReportSearch.class)
            .allFieldsShouldBeUsed().suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void canSerialize() throws JsonMappingException, JsonProcessingException {
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(BOTH);
        search.setLabelFilters(ImmutableList.of("labelFilters"));
        search.setAdherenceMax(53);
        search.setProgressionFilter(DONE);
        search.setIdFilter("idFilter");
        search.setOffsetBy(5);
        search.setPageSize(10);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(search);
        assertEquals(node.get("testFilter").textValue(), "both");
        assertEquals(node.get("labelFilters").get(0).textValue(), "labelFilters");
        assertEquals(node.get("adherenceMin").intValue(), 0);
        assertEquals(node.get("adherenceMax").intValue(), 53);
        assertEquals(node.get("progressionFilter").textValue(), "done");
        assertEquals(node.get("idFilter").textValue(), "idFilter");
        assertEquals(node.get("offsetBy").intValue(), 5);
        assertEquals(node.get("pageSize").intValue(), 10);
        
        
        AdherenceReportSearch deser = BridgeObjectMapper.get()
                .readValue(node.toString(), AdherenceReportSearch.class);
        assertEquals(deser, search);
    }
    
    @Test
    public void nullValuesDoNotOverrideInitialObjectFields() throws JsonMappingException, JsonProcessingException {
        // These are explicit nulls. Does Jackson overwrite these values?
        String json = "{\"adherenceMin\":null,\"adherenceMax\":null,\"offsetBy\":null,\"pageSize\":null}";
        
        AdherenceReportSearch deser = BridgeObjectMapper.get()
                .readValue(json, AdherenceReportSearch.class);
        assertEquals(deser.getAdherenceMin(), Integer.valueOf(0));
        assertEquals(deser.getAdherenceMax(), Integer.valueOf(100));
        assertEquals(deser.getOffsetBy(), Integer.valueOf(0));
        assertEquals(deser.getPageSize(), Integer.valueOf(API_DEFAULT_PAGE_SIZE));
    }

}
