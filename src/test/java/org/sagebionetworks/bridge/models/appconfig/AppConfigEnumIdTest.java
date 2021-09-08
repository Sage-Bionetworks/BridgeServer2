package org.sagebionetworks.bridge.models.appconfig;

import static org.sagebionetworks.bridge.models.appconfig.AppConfigEnumId.STUDY_DESIGN_TYPES;
import static org.sagebionetworks.bridge.models.appconfig.AppConfigEnumId.STUDY_DISEASES;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class AppConfigEnumIdTest {
    
    @Test
    public void test() {
        assertEquals(STUDY_DISEASES.getAppConfigKey(), "bridge:study-diseases");
        assertEquals(STUDY_DESIGN_TYPES.getAppConfigKey(), "bridge:study-design-types");
    }

}
