package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class FileReferenceTest {
    
    private static final String HREF = BridgeConfigFactory.getConfig()
            .getHostnameWithPostfix("docs") + "/" + GUID + "." + TIMESTAMP.getMillis();

    @Test
    public void test() {
        FileReference ref = new FileReference(GUID, TIMESTAMP);
        
        assertEquals(ref.getGuid(), GUID);
        assertEquals(ref.getCreatedOn(), TIMESTAMP);
        assertTrue(ref.getHref().contains(HREF));
    }
 
}
