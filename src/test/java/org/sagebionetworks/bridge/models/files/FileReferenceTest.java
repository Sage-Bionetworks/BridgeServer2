package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.config.Environment.UAT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

@PrepareForTest({BridgeConfigFactory.class})
public class FileReferenceTest extends PowerMockTestCase {
    
    private BridgeConfig config;
    
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(FileReference.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void nullGuid() throws Exception {
        FileReference ref = new FileReference(null, TIMESTAMP);
        assertNull(ref.getGuid());
        assertEquals(ref.getCreatedOn(), TIMESTAMP);
        assertNull(ref.getHref());
    }
    
    @Test
    public void nullCreatedOn() throws Exception {
        FileReference ref = new FileReference(GUID, null);
        assertEquals(ref.getGuid(), GUID);
        assertNull(ref.getCreatedOn());
        assertNull(ref.getHref());
    }
    
    @Test
    public void canSerialization() throws Exception {
        String href = "http://" + BridgeConfigFactory.getConfig()
                .getHostnameWithPostfix("docs") + "/" + GUID + "." + TIMESTAMP.getMillis();
        
        FileReference ref = new FileReference(GUID, TIMESTAMP);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(ref);
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("type").textValue(), "FileReference");
        
        FileReference deser = BridgeObjectMapper.get().readValue(node.toString(), FileReference.class);
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
        assertEquals(deser.getHref(), href);
    }
 
    @Test
    public void testWithProdEnv() {
        PowerMockito.mockStatic(BridgeConfigFactory.class);
        config = Mockito.mock(BridgeConfig.class);
        Mockito.when(BridgeConfigFactory.getConfig()).thenReturn(config);
        
        Mockito.when(config.getEnvironment()).thenReturn(PROD);
        Mockito.when(config.getHostnameWithPostfix("docs")).thenReturn("docs.test.com");
        
        FileReference ref = new FileReference(GUID, TIMESTAMP);
        assertEquals(ref.getHref(), "https://docs.test.com/oneGuid." + TIMESTAMP.getMillis());
    }

    @Test
    public void testWithStagingEnv() {
        PowerMockito.mockStatic(BridgeConfigFactory.class);
        config = Mockito.mock(BridgeConfig.class);
        Mockito.when(BridgeConfigFactory.getConfig()).thenReturn(config);
        
        Mockito.when(config.getEnvironment()).thenReturn(UAT);
        Mockito.when(config.getHostnameWithPostfix("docs")).thenReturn("docs-staging.test.com");
        
        FileReference ref = new FileReference(GUID, TIMESTAMP);
        assertEquals(ref.getHref(), "http://docs-staging.test.com/oneGuid." + TIMESTAMP.getMillis());
    }
}
