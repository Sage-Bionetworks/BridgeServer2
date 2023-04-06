
package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import com.google.common.collect.ImmutableSet;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import java.util.HashSet;

public class AdherenceRecordTest extends Mockito {

    AdherenceRecord record;
    
    @Test
    public void canSerialize() throws Exception { 
        AdherenceRecord record = new AdherenceRecord();
        record.setAppId(TEST_APP_ID);
        record.setUserId(TEST_USER_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setStartedOn(CREATED_ON);
        record.setFinishedOn(MODIFIED_ON);
        record.setUploadedOn(MODIFIED_ON.plusHours(1));
        record.setEventTimestamp(CREATED_ON.plusHours(1));
        record.setInstanceTimestamp(CREATED_ON.plusHours(2)); // doesn't serialize
        record.setClientData(TestUtils.getClientData());
        record.setClientTimeZone("America/Los_Angeles");
        record.setDeclined(true);
        record.setInstanceGuid(GUID);
        record.setAssessmentGuid("assessmentGuid");
        record.setSessionGuid("sessionGuid"); // in reality, both of these won't be set
        record.setUploadIds(ImmutableSet.of("instanceGuid"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(record);
        assertEquals(node.size(), 13);
        assertEquals(node.get("startedOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("finishedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("uploadedOn").textValue(), MODIFIED_ON.plusHours(1).toString());
        assertEquals(node.get("eventTimestamp").textValue(), CREATED_ON.plusHours(1).toString());
        assertEquals(node.get("clientData").get("intValue").intValue(), 4);
        assertEquals(node.get("instanceGuid").textValue(), GUID);
        assertEquals(node.get("clientTimeZone").textValue(), "America/Los_Angeles");
        assertEquals(node.get("assessmentGuid").textValue(), "assessmentGuid");
        assertEquals(node.get("sessionGuid").textValue(), "sessionGuid");
        assertTrue(node.get("declined").booleanValue());
        assertEquals(node.get("userId").textValue(), TEST_USER_ID);
        assertEquals(node.get("uploadIds").get(0).textValue(), "instanceGuid");
        assertEquals(node.get("type").textValue(), "AdherenceRecord");
        
        AdherenceRecord deser = BridgeObjectMapper.get()
                .readValue(node.toString(), AdherenceRecord.class);
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getStartedOn(), CREATED_ON);
        assertEquals(deser.getFinishedOn(), MODIFIED_ON);
        assertEquals(deser.getUploadedOn(), MODIFIED_ON.plusHours(1));
        assertEquals(deser.getEventTimestamp(), CREATED_ON.plusHours(1));
        assertNotNull(deser.getClientData());
        assertEquals(deser.getInstanceGuid(), GUID);
        assertEquals(deser.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(deser.getInstanceGuid(), GUID);
        assertEquals(deser.getAssessmentGuid(), "assessmentGuid");
        assertEquals(deser.getSessionGuid(), "sessionGuid");
        assertEquals(deser.getUploadIds(), ImmutableSet.of("instanceGuid"));
        assertTrue(deser.isDeclined());
    }
    
    @Test
    public void getUploadIds_returnsEmptyWhenNull() {
        AdherenceRecord record = new AdherenceRecord();
        assertTrue(record.getUploadIds().isEmpty());
        
        record.setUploadIds(new HashSet<>());
        assertTrue(record.getUploadIds().isEmpty());
    }
    
    @Test
    public void addUploadId_setNull() {
        AdherenceRecord record = new AdherenceRecord();
        record.addUploadId("uploadId");
        
        assertEquals(record.getUploadIds().size(), 1);
        assertTrue(record.getUploadIds().contains("uploadId"));
    }
    
    @Test
    public void addUploadId_setExists() {
        AdherenceRecord record = new AdherenceRecord();
        record.setUploadIds(new HashSet<>());
        
        record.addUploadId("uploadId");
        assertEquals(record.getUploadIds().size(), 1);
        assertTrue(record.getUploadIds().contains("uploadId"));
    }
}
