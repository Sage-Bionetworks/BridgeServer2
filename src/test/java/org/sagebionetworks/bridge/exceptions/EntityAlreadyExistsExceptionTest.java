package org.sagebionetworks.bridge.exceptions;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.spring.handlers.BridgeExceptionHandler;

public class EntityAlreadyExistsExceptionTest {

    /**
     * Some entities are not exposed through the API and when such an internal entity already exists, we cannot return
     * the object the user just submitted to us. The exception should still work.
     */
    @Test
    public void exceptionSerializesWithoutEntity() throws Exception {
        EntityAlreadyExistsException e = new EntityAlreadyExistsException(ExternalIdentifier.class, null);
        assertEquals(e.getMessage(), "ExternalIdentifier already exists.");
        assertEquals(e.getEntity().entrySet().size(), 0);
        assertEquals(e.getEntityClass(), "ExternalIdentifier");
    }
    
    @Test
    public void exceptionWithEntityKeys() throws Exception {
        Map<String,Object> map = new ImmutableMap.Builder<String,Object>().put("key", "value").build();
        EntityAlreadyExistsException e = new EntityAlreadyExistsException(ExternalIdentifier.class, map);
        
        assertEquals(e.getMessage(), "ExternalIdentifier already exists.");
        assertEquals(e.getEntity().entrySet().size(), 1);
        assertEquals(e.getEntity().get("key"), "value");
        assertEquals(e.getEntityClass(), "ExternalIdentifier");
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void exceptionInvalidConstruction() {
        new EntityAlreadyExistsException(null, null);
    }
    
    @Test
    public void serializesCorrectlyThroughInterceptor() throws Throwable {
        EntityAlreadyExistsException e = new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", "foo");
        BridgeExceptionHandler handler = new BridgeExceptionHandler();
        HttpServletRequest mockRequest = new MockHttpServletRequest();
        
        ResponseEntity<String> entity = handler.handleException(mockRequest, e);
        
        assertEquals(entity.getStatusCodeValue(), 409);
        JsonNode node = BridgeObjectMapper.get().readTree(entity.getBody());
        
        assertEquals(node.get("message").textValue(), "ExternalIdentifier already exists.");
        assertEquals(node.get("type").textValue(), "EntityAlreadyExistsException");
        assertEquals(node.get("entityClass").textValue(), "ExternalIdentifier");
        assertEquals(node.get("statusCode").intValue(), 409);
        assertEquals(node.size(), 6);
        
        JsonNode model = node.get("entity");
        assertEquals(model.get("identifier").textValue(), "foo");
        
        JsonNode modelKeys = node.get("entityKeys");
        assertEquals(modelKeys.get("identifier").textValue(), "foo");
    }
}
