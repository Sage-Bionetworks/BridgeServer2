package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.ASC;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.DESC;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AdherenceRecordsSearchTest extends Mockito {
    
    @Test
    public void canSerialize() throws Exception {
        AdherenceRecordsSearch search = createSearch();
                
        JsonNode node = BridgeObjectMapper.get().valueToTree(search);
        assertEquals(node.size(), 15);
        assertEquals(node.get("userId").textValue(), TEST_USER_ID);
        assertEquals(node.get("studyId").textValue(), TEST_STUDY_ID);
        assertEquals(node.get("instanceGuids").get(0).textValue(), "A");
        assertEquals(node.get("assessmentIds").get(0).textValue(), "B");
        assertEquals(node.get("sessionGuids").get(0).textValue(), "C");
        assertEquals(node.get("timeWindowGuids").get(0).textValue(), "D");
        assertEquals(node.get("recordType").textValue(), "session");
        assertTrue(node.get("includeRepeats").booleanValue());
        assertEquals(node.get("eventTimestamps").get("E").textValue(), CREATED_ON.toString());
        assertEquals(node.get("startTime").textValue(), CREATED_ON.toString());
        assertEquals(node.get("endTime").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("offsetBy").intValue(), 100);
        assertEquals(node.get("pageSize").intValue(), 20);
        assertEquals(node.get("sortOrder").textValue(), "desc");
        assertEquals(node.get("type").textValue(), "AdherenceRecordsSearch");
        assertNull(node.get("guidToStartedOnMap"));
        
        AdherenceRecordsSearch deser = BridgeObjectMapper.get()
                .readValue(node.toString(), AdherenceRecordsSearch.class);
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getStudyId(), TEST_STUDY_ID);
        assertEquals(deser.getInstanceGuids(), ImmutableSet.of("A"));
        assertEquals(deser.getAssessmentIds(), ImmutableSet.of("B"));
        assertEquals(deser.getSessionGuids(), ImmutableSet.of("C"));
        assertEquals(deser.getTimeWindowGuids(), ImmutableSet.of("D"));
        assertEquals(deser.getRecordType(), SESSION);
        assertTrue(deser.getIncludeRepeats());
        assertEquals(deser.getEventTimestamps().get("E"), CREATED_ON);
        assertEquals(deser.getStartTime(), CREATED_ON);
        assertEquals(deser.getEndTime(), MODIFIED_ON);
        assertEquals(deser.getOffsetBy(), Integer.valueOf(100));
        assertEquals(deser.getPageSize(), Integer.valueOf(20));
        assertEquals(deser.getSortOrder(), DESC);
        assertEquals(deser.getInstanceGuidStartedOnMap(), ImmutableMap.of());
    }
    
    @Test
    public void canCopy() {
        AdherenceRecordsSearch search = createSearch();
        
        AdherenceRecordsSearch copy = new AdherenceRecordsSearch.Builder()
                .copyOf(search).build();
        assertEquals(copy.getUserId(), TEST_USER_ID);
        assertEquals(copy.getStudyId(), TEST_STUDY_ID);
        assertEquals(copy.getInstanceGuids(), ImmutableSet.of("A"));
        assertEquals(copy.getAssessmentIds(), ImmutableSet.of("B"));
        assertEquals(copy.getSessionGuids(), ImmutableSet.of("C"));
        assertEquals(copy.getTimeWindowGuids(), ImmutableSet.of("D"));
        assertEquals(copy.getRecordType(), SESSION);
        assertTrue(copy.getIncludeRepeats());
        assertEquals(copy.getEventTimestamps().get("E"), CREATED_ON);
        assertEquals(copy.getInstanceGuidStartedOnMap().get("E"), CREATED_ON);
        assertEquals(copy.getStartTime(), CREATED_ON);
        assertEquals(copy.getEndTime(), MODIFIED_ON);
        assertEquals(copy.getOffsetBy(), Integer.valueOf(100));
        assertEquals(copy.getPageSize(), Integer.valueOf(20));
        assertEquals(copy.getSortOrder(), DESC);
    }

    protected AdherenceRecordsSearch createSearch() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withInstanceGuids(ImmutableSet.of("A"))
                .withAssessmentIds(ImmutableSet.of("B"))
                .withSessionGuids(ImmutableSet.of("C"))
                .withTimeWindowGuids(ImmutableSet.of("D"))
                .withRecordType(SESSION)
                .withIncludeRepeats(Boolean.TRUE)
                .withEventTimestamps(ImmutableMap.of("E", CREATED_ON))
                .withInstanceGuidStartedOnMap(ImmutableMap.of("E", CREATED_ON))
                .withStartTime(CREATED_ON)
                .withEndTime(MODIFIED_ON)
                .withOffsetBy(100)
                .withPageSize(20)
                .withSortOrder(DESC)
                .build();
        return search;
    }
    
    @Test
    public void setsDefaults() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder().build();
        assertEquals(search.getInstanceGuids(), ImmutableSet.of());
        assertEquals(search.getAssessmentIds(), ImmutableSet.of());
        assertEquals(search.getSessionGuids(), ImmutableSet.of());
        assertEquals(search.getTimeWindowGuids(), ImmutableSet.of());
        assertTrue(search.getIncludeRepeats());
        assertEquals(search.getEventTimestamps(), ImmutableMap.of());
        assertEquals(search.getOffsetBy(), Integer.valueOf(0));
        assertEquals(search.getPageSize(), Integer.valueOf(DEFAULT_PAGE_SIZE));
        assertEquals(search.getSortOrder(), ASC);
    }

}
