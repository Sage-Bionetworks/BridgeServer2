package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.UA;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.persistence.PersistenceException;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;

public class ClientInfoConverterTest extends Mockito {

    static final ClientInfoConverter CONVERTER = new ClientInfoConverter();
    
    @Test
    public void convertToDatabaseColumn() throws Exception { 
        ClientInfo info = ClientInfo.fromUserAgentCache(UA);
        String json = CONVERTER.convertToDatabaseColumn(info);
        ClientInfo deser = BridgeObjectMapper.get().readValue(json, ClientInfo.class);
        
        assertEquals(deser, info);
    }
    
    // Nothing in the javadocs about whether null *will* be passed to this converter...
    @Test
    public void convertToDatabaseColumnNull() throws Exception { 
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }
    
    @Test
    public void convertToEntityAttribute() throws Exception {
        ClientInfo info = ClientInfo.fromUserAgentCache(UA);
        String json = BridgeObjectMapper.get().writeValueAsString(info);
        
        ClientInfo deser = CONVERTER.convertToEntityAttribute(json);
        assertEquals(deser, info);
    }
    
    @Test
    public void convertToEntityAttributeNull() throws Exception {
        ClientInfo deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void convertToEntityAttributeJsonErrorThrows() throws Exception {
        ClientInfo deser = CONVERTER.convertToEntityAttribute("not json");
        assertNull(deser);
    }
}
