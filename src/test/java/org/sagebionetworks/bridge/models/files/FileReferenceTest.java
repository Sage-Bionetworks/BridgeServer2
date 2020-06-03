package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestUtils.mockConfigResolver;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.config.Environment.UAT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FileReferenceTest extends Mockito {
    
    @Mock
    BridgeConfig mockConfig;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(FileReference.class).allFieldsShouldBeUsedExcept("resolver").verify();
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
    public void canSerialize() throws Exception {
        ConfigResolver resolver = mockConfigResolver(LOCAL, "docs");
        FileReference ref = new FileReference(resolver, GUID, TIMESTAMP);
        
        String href = "http://docs-local.bridge.org/" + GUID + "." + TIMESTAMP.getMillis();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(ref);
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("href").textValue(), href);
        assertEquals(node.get("type").textValue(), "FileReference");
        
        FileReference deser = BridgeObjectMapper.get().readValue(node.toString(), FileReference.class);
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
        // The href will pick up the default resolver so the URL would be different,
        // but as deployed, this is correct.
    }
 
    @Test
    public void testWithProdEnv() {
        ConfigResolver resolver = mockConfigResolver(PROD, "docs");
        FileReference ref = new FileReference(resolver, GUID, TIMESTAMP);
        
        assertEquals(ref.getHref(), "https://docs-prod.bridge.org/oneGuid." + TIMESTAMP.getMillis());
    }

    @Test
    public void testWithStagingEnv() {
        ConfigResolver resolver = mockConfigResolver(UAT, "docs");
        FileReference ref = new FileReference(resolver, GUID, TIMESTAMP);
        
        assertEquals(ref.getHref(), "https://docs-uat.bridge.org/oneGuid." + TIMESTAMP.getMillis());
    }
}
