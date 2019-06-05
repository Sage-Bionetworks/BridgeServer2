package org.sagebionetworks.bridge.exceptions;

import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;

import org.aopalliance.intercept.MethodInvocation;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.spring.handlers.BridgeExceptionHandler;

public class ConstraintViolationExceptionTest extends Mockito {

    @Mock
    MethodInvocation invocation;
    
    @Test
    public void testConstruction() {
        ConstraintViolationException e = createException();
        
        assertEquals(e.getMessage(), "Referenced in survey");
        assertEquals(e.getReferrerKeys().get("surveyGuid"), "surveyGuidValue");
        assertEquals(e.getReferrerKeys().get("createdOn"), "createdOnValue");
        assertEquals(e.getEntityKeys().get("schemaId"), "schemaIdValue");
        assertEquals(e.getEntityKeys().get("schemaRevision"), "10");
    }
    
    @Test
    public void serializesCorrectlyThroughInterceptor() throws Throwable {
        BridgeExceptionHandler handler = new BridgeExceptionHandler();
        HttpServletRequest mockRequest = new MockHttpServletRequest();
        ConstraintViolationException e = createException();
        
        ResponseEntity<String> entity = handler.handleException(mockRequest, e);
        
        assertEquals(entity.getStatusCodeValue(), 409);
        JsonNode node = BridgeObjectMapper.get().readTree(entity.getBody());
        
        assertEquals(node.get("message").textValue(), "Referenced in survey");
        assertEquals(node.get("type").textValue(), "ConstraintViolationException");
        assertEquals(node.get("statusCode").intValue(), 409);
        
        JsonNode entityKeys = node.get("entityKeys");
        assertEquals(entityKeys.get("schemaId").textValue(), "schemaIdValue");
        assertEquals(entityKeys.get("schemaRevision").textValue(), "10");
        
        JsonNode referrerKeys = node.get("referrerKeys");
        assertEquals(referrerKeys.get("surveyGuid").textValue(), "surveyGuidValue");
        assertEquals(referrerKeys.get("createdOn").textValue(), "createdOnValue");
        assertEquals(node.size(), 5);
    }
    
    @Test
    public void hasDefaultMessage() {
        ConstraintViolationException e = new ConstraintViolationException.Builder()
                .withReferrerKey("surveyGuid", "surveyGuidValue")
                .withReferrerKey("createdOn", "createdOnValue")
                .withEntityKey("schemaId", "schemaIdValue")
                .withEntityKey("schemaRevision", "10").build();
        
        assertEquals(e.getMessage(),
                "Operation not permitted because entity {surveyGuid=surveyGuidValue, createdOn=createdOnValue} refers to this entity {schemaId=schemaIdValue, schemaRevision=10}.");
    }
    
    @Test
    public void collectionsExistEvenIfEmpty() throws Throwable {
        ConstraintViolationException e = new ConstraintViolationException.Builder()
                .withMessage("Referenced in survey").build();
        
        BridgeExceptionHandler handler = new BridgeExceptionHandler();
        HttpServletRequest mockRequest = new MockHttpServletRequest();
        
        ResponseEntity<String> entity = handler.handleException(mockRequest, e);

        assertEquals(entity.getStatusCodeValue(), 409);
        JsonNode node = BridgeObjectMapper.get().readTree(entity.getBody());
        
        assertEquals(node.size(), 5);
        assertEquals(node.get("entityKeys").size(), 0);
        assertEquals(node.get("referrerKeys").size(), 0);
    }

    private ConstraintViolationException createException() {
        return new ConstraintViolationException.Builder()
                .withMessage("Referenced in survey")
                .withReferrerKey("surveyGuid", "surveyGuidValue")
                .withReferrerKey("createdOn", "createdOnValue")
                .withEntityKey("schemaId", "schemaIdValue")
                .withEntityKey("schemaRevision", "10").build();
    }
}
