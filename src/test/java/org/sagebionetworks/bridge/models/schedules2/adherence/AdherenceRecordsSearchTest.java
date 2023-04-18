package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.OR;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.ASC;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.DESC;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class AdherenceRecordsSearchTest extends Mockito {
    private static final DateTime EVENT_TIMESTAMP_START = DateTime.parse("2023-04-11T14:36:47.529Z");
    private static final DateTime EVENT_TIMESTAMP_END = DateTime.parse("2023-04-11T14:36:58.871Z");
    private static final String UPLOAD_ID = "test-upload-id";

    @Test
    public void canSerialize() throws Exception {
        AdherenceRecordsSearch search = createSearch();
                
        JsonNode node = BridgeObjectMapper.get().valueToTree(search);
        assertEquals(node.size(), 25);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("userId").textValue(), TEST_USER_ID);
        assertEquals(node.get("studyId").textValue(), TEST_STUDY_ID);
        assertEquals(node.get("instanceGuids").get(0).textValue(), "A");
        assertEquals(node.get("assessmentIds").get(0).textValue(), "B");
        assertEquals(node.get("sessionGuids").get(0).textValue(), "C");
        assertEquals(node.get("timeWindowGuids").get(0).textValue(), "D");
        assertEquals(node.get("adherenceRecordType").textValue(), "session");
        assertTrue(node.get("includeRepeats").booleanValue());
        assertTrue(node.get("currentTimestampsOnly").booleanValue());
        assertEquals(node.get("eventTimestamps").get("E").textValue(), CREATED_ON.toString());
        assertEquals(node.get("eventTimestampStart").textValue(), EVENT_TIMESTAMP_START.toString());
        assertEquals(node.get("eventTimestampEnd").textValue(), EVENT_TIMESTAMP_END.toString());
        assertEquals(node.get("startTime").textValue(), CREATED_ON.toString());
        assertEquals(node.get("endTime").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("offsetBy").intValue(), 100);
        assertEquals(node.get("pageSize").intValue(), 20);
        assertEquals(node.get("sortOrder").textValue(), "desc");
        assertEquals(node.get("type").textValue(), "AdherenceRecordsSearch");
        assertEquals(node.get("predicate").textValue(), "or");
        assertEquals(node.get("stringSearchPosition").textValue(), "infix");
        assertNull(node.get("guidToStartedOnMap"));
        assertTrue(node.get("declined").booleanValue());
        assertEquals(node.get("uploadId").textValue(), UPLOAD_ID);
        assertTrue(node.get("hasMultipleUploadIds").booleanValue());
        assertTrue(node.get("hasNoUploadIds").booleanValue());

        AdherenceRecordsSearch deser = BridgeObjectMapper.get()
                .readValue(node.toString(), AdherenceRecordsSearch.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getStudyId(), TEST_STUDY_ID);
        assertEquals(deser.getInstanceGuids(), ImmutableSet.of("A"));
        assertEquals(deser.getAssessmentIds(), ImmutableSet.of("B"));
        assertEquals(deser.getSessionGuids(), ImmutableSet.of("C"));
        assertEquals(deser.getTimeWindowGuids(), ImmutableSet.of("D"));
        assertEquals(deser.getAdherenceRecordType(), SESSION);
        assertTrue(deser.getIncludeRepeats());
        assertTrue(deser.getCurrentTimestampsOnly());
        assertEquals(deser.getEventTimestamps().get("E"), CREATED_ON);
        assertEquals(deser.getEventTimestampStart(), EVENT_TIMESTAMP_START);
        assertEquals(deser.getEventTimestampEnd(), EVENT_TIMESTAMP_END);
        assertEquals(deser.getStartTime(), CREATED_ON);
        assertEquals(deser.getEndTime(), MODIFIED_ON);
        assertEquals(deser.getOffsetBy(), Integer.valueOf(100));
        assertEquals(deser.getPageSize(), Integer.valueOf(20));
        assertEquals(deser.getSortOrder(), DESC);
        assertEquals(deser.getInstanceGuidStartedOnMap(), ImmutableMap.of());
        assertEquals(deser.getPredicate(), OR);
        assertTrue(deser.isDeclined());
        assertEquals(deser.getUploadId(), UPLOAD_ID);
        assertTrue(deser.hasMultipleUploadIds());
        assertTrue(deser.hasNoUploadIds());
    }
    
    // We don't want these to throw 500s. These probably exist elsewhere in our APIs.
    @Test
    public void canDeserializeNulls() throws Exception {
        String json = TestUtils.createJson("{'instanceGuids':[null],'eventTimestamps':{'enrollment':null}, 'searchTermPredicate':null}");
        
        AdherenceRecordsSearch deser = BridgeObjectMapper.get()
                .readValue(json, AdherenceRecordsSearch.class);
        assertTrue(deser.getInstanceGuids().isEmpty());
        assertTrue(deser.getEventTimestamps().isEmpty());
        
        deser = deser.toBuilder().build();
        assertTrue(deser.getInstanceGuids().isEmpty());
        assertTrue(deser.getEventTimestamps().isEmpty());
        assertEquals(deser.getPredicate(), AND);

        // hasMultipleUploadIds and hasNoUploadIds default to false.
        assertFalse(deser.hasMultipleUploadIds());
        assertFalse(deser.hasNoUploadIds());
    }
    
    @Test
    public void toBuilder() {
        AdherenceRecordsSearch search = createSearch();
        
        AdherenceRecordsSearch copy = search.toBuilder().build();
        assertEquals(copy.getAppId(), TEST_APP_ID);
        assertEquals(copy.getUserId(), TEST_USER_ID);
        assertEquals(copy.getStudyId(), TEST_STUDY_ID);
        assertEquals(copy.getInstanceGuids(), ImmutableSet.of("A"));
        assertEquals(copy.getAssessmentIds(), ImmutableSet.of("B"));
        assertEquals(copy.getSessionGuids(), ImmutableSet.of("C"));
        assertEquals(copy.getTimeWindowGuids(), ImmutableSet.of("D"));
        assertEquals(copy.getAdherenceRecordType(), SESSION);
        assertTrue(copy.getIncludeRepeats());
        assertTrue(copy.getCurrentTimestampsOnly());
        assertEquals(copy.getEventTimestamps().get("E"), CREATED_ON);
        assertEquals(copy.getEventTimestampStart(), EVENT_TIMESTAMP_START);
        assertEquals(copy.getEventTimestampEnd(), EVENT_TIMESTAMP_END);
        assertEquals(copy.getInstanceGuidStartedOnMap().get("E"), CREATED_ON);
        assertEquals(copy.getStartTime(), CREATED_ON);
        assertEquals(copy.getEndTime(), MODIFIED_ON);
        assertEquals(copy.getOffsetBy(), Integer.valueOf(100));
        assertEquals(copy.getPageSize(), Integer.valueOf(20));
        assertEquals(copy.getSortOrder(), DESC);
        assertEquals(copy.getPredicate(), OR);
        assertEquals(copy.isDeclined(), Boolean.TRUE);
        assertEquals(copy.getUploadId(), UPLOAD_ID);
        assertTrue(copy.hasMultipleUploadIds());
        assertTrue(copy.hasNoUploadIds());
    }

    protected AdherenceRecordsSearch createSearch() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withInstanceGuids(ImmutableSet.of("A"))
                .withAssessmentIds(ImmutableSet.of("B"))
                .withSessionGuids(ImmutableSet.of("C"))
                .withTimeWindowGuids(ImmutableSet.of("D"))
                .withAdherenceRecordType(SESSION)
                .withIncludeRepeats(Boolean.TRUE)
                .withCurrentTimestampsOnly(Boolean.TRUE)
                .withEventTimestamps(ImmutableMap.of("E", CREATED_ON))
                .withEventTimestampStart(EVENT_TIMESTAMP_START)
                .withEventTimestampEnd(EVENT_TIMESTAMP_END)
                .withInstanceGuidStartedOnMap(ImmutableMap.of("E", CREATED_ON))
                .withStartTime(CREATED_ON)
                .withEndTime(MODIFIED_ON)
                .withOffsetBy(100)
                .withPageSize(20)
                .withSortOrder(DESC)
                .withPredicate(OR)
                .withDeclined(Boolean.TRUE)
                .withUploadId(UPLOAD_ID)
                .withHasMultipleUploadIds(true)
                .withHasNoUploadIds(true)
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
        assertFalse(search.getCurrentTimestampsOnly());
        assertEquals(search.getEventTimestamps(), ImmutableMap.of());
        assertEquals(search.getOffsetBy(), Integer.valueOf(0));
        assertEquals(search.getPageSize(), Integer.valueOf(DEFAULT_PAGE_SIZE));
        assertEquals(search.getSortOrder(), ASC);
        assertEquals(search.getPredicate(), AND);
        assertFalse(search.hasMultipleUploadIds());
        assertFalse(search.hasNoUploadIds());
    }
}
