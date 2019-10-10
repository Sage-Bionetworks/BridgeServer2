package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.config.Environment.UAT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FileReferenceTest {
    
    private static final String HREF = BridgeConfigFactory.getConfig()
            .getHostnameWithPostfix("docs") + "/" + GUID + "." + TIMESTAMP.getMillis();

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(FileReference.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void nullValues() throws Exception {
        FileReference ref = BridgeObjectMapper.get().readValue("{}", FileReference.class);
        assertNull(ref.getGuid());
        assertNull(ref.getCreatedOn());
        assertNull(ref.getHref());
    }
    
    @Test
    public void test() {
        FileReference ref = new FileReference(GUID, TIMESTAMP);
        
        assertEquals(ref.getGuid(), GUID);
        assertEquals(ref.getCreatedOn(), TIMESTAMP);
        assertTrue(ref.getHref().contains(HREF));
    }
 
    @Test
    public void testWithProdEnv() {
        FileReference ref = new FileReference(PROD, "docs.test.com", GUID, TIMESTAMP);
        assertEquals(ref.getHref(), "https://docs.test.com/oneGuid." + TIMESTAMP.getMillis());
    }

    @Test
    public void testWithStagingEnv() {
        FileReference ref = new FileReference(UAT, "docs.test.com", GUID, TIMESTAMP);
        assertEquals(ref.getHref(), "http://docs.test.com/oneGuid." + TIMESTAMP.getMillis());
    }
}
