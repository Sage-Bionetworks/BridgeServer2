package org.sagebionetworks.bridge.exceptions;

import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.spring.handlers.BridgeExceptionHandler;

public class LimitExceededExceptionTest {

    @Test
    public void serializesCorrectly() throws Throwable {
        BridgeExceptionHandler handler = new BridgeExceptionHandler();
        HttpServletRequest mockRequest = new MockHttpServletRequest();
        LimitExceededException e = new LimitExceededException("Too many");
        
        ResponseEntity<String> entity = handler.handleException(mockRequest, e);
        assertEquals(entity.getStatusCodeValue(), 429);
        
        JsonNode node = new ObjectMapper().readTree(entity.getBody());
        assertEquals(node.get("statusCode").intValue(), 429);
        assertEquals(node.get("message").textValue(), "Too many");
        assertEquals(node.get("type").textValue(), "LimitExceededException");
        assertEquals(node.size(), 3);
    }

}
