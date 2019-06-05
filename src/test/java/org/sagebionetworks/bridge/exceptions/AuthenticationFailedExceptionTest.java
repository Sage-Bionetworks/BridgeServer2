package org.sagebionetworks.bridge.exceptions;

import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.spring.handlers.BridgeExceptionHandler;

public class AuthenticationFailedExceptionTest {
    
    @Test
    public void serializesCorrectly() throws Throwable {
        BridgeExceptionHandler handler = new BridgeExceptionHandler();
        HttpServletRequest mockRequest = new MockHttpServletRequest();
        AuthenticationFailedException e = new AuthenticationFailedException();
        
        ResponseEntity<String> entity = handler.handleException(mockRequest, e);
        assertEquals(entity.getStatusCode(), HttpStatus.UNAUTHORIZED);
        assertEquals(entity.getStatusCodeValue(), 401);
        
        JsonNode node = new ObjectMapper().readTree(entity.getBody());
        assertEquals(node.get("statusCode").intValue(), 401);
        assertEquals(node.get("message").textValue(), "Authentication failed.");
        assertEquals(node.get("type").textValue(), "AuthenticationFailedException");
        assertEquals(node.size(), 3);
    }
}
