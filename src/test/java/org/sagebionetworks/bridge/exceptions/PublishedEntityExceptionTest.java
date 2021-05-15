package org.sagebionetworks.bridge.exceptions;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.spring.handlers.BridgeExceptionHandler;

public class PublishedEntityExceptionTest {
    
    @Test
    public void serializesCorrectly() throws Exception {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        
        PublishedEntityException pe = new PublishedEntityException(schedule);
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(pe);
        // This is normally done by the BridgeExceptionHandler...
        node.remove(BridgeExceptionHandler.UNEXPOSED_FIELD_NAMES);
        
        assertEquals(node.size(), 3);
        assertEquals(node.get("statusCode").intValue(), 400);
        assertEquals(node.get("message").textValue(), "A Schedule cannot be updated after publication.");
        assertEquals(node.get("type").textValue(), "PublishedEntityException");
    }
}
