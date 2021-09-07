package org.sagebionetworks.bridge.models.appconfig;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

public class AppConfigEnumTest {

    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson(
                "{'allowOther':false, 'en': ['a','b','c'], 'fr': ['d', 'e', 'f']}"); 
        
        AppConfigEnum enumerated = BridgeObjectMapper.get().readValue(json, AppConfigEnum.class);
        System.out.println(enumerated);
    }
}
