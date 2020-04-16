package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class PasswordResetTest {

    @Test
    public void canDeserialize() throws Exception {
        String json = TestUtils.createJson("{'sptoken': '3x9HSBY3vZr5zrx9qkEtLa', "+
                "'password': 'pass', 'study': '" + TEST_APP_ID + "'}");
        
        PasswordReset reset = BridgeObjectMapper.get().readValue(json, PasswordReset.class);
        assertEquals(reset.getSptoken(), "3x9HSBY3vZr5zrx9qkEtLa");
        assertEquals(reset.getPassword(), "pass");
        assertEquals(reset.getStudyIdentifier(), TEST_APP_ID);
    }
    
}
