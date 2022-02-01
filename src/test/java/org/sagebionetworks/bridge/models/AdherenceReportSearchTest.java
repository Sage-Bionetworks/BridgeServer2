package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.models.AccountTestFilter.BOTH;
import static org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress.DONE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress.IN_PROGRESS;
import static org.testng.Assert.assertEquals;

import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

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
        search.setLabelFilters(ImmutableSet.of("labelFilters"));
        search.setAdherenceMax(53);
        search.setProgressionFilters(ImmutableSet.of(IN_PROGRESS, DONE));
        search.setIdFilter("idFilter");
        search.setOffsetBy(5);
        search.setPageSize(10);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(search);
        assertEquals(node.get("testFilter").textValue(), "both");
        assertEquals(node.get("labelFilters").get(0).textValue(), "labelFilters");
        assertEquals(node.get("adherenceMin").intValue(), 0);
        assertEquals(node.get("adherenceMax").intValue(), 53);
        Set<String> progressions = ImmutableSet.of(
                node.get("progressionFilters").get(0).textValue(),
                node.get("progressionFilters").get(1).textValue());
        assertEquals(progressions, ImmutableSet.of("in_progress", "done"));
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
