package org.sagebionetworks.bridge.models.assessments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

import nl.jqno.equalsverifier.EqualsVerifier;

@PrepareForTest({BridgeConfigFactory.class})
public class AssessmentReferenceTest extends PowerMockTestCase {

    private BridgeConfig config;
    
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(AssessmentReference.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void succeeds() throws Exception {
        PowerMockito.mockStatic(BridgeConfigFactory.class);
        config = mock(BridgeConfig.class);
        when(config.getEnvironment()).thenReturn(Environment.UAT);
        when(config.getHostnameWithPostfix("ws")).thenReturn("ws-staging.sagebridge.org");
        when(BridgeConfigFactory.getConfig()).thenReturn(config);
        
        String href = "https://ws-staging.sagebridge.org/v1/assessments/oneGuid/config";
        
        AssessmentReference ref = new AssessmentReference("oneGuid", "id", "sharedId");
        
        assertEquals(ref.getGuid(), "oneGuid");
        assertEquals(ref.getId(), "id");
        assertEquals(ref.getSharedId(), "sharedId");
        assertEquals(ref.getConfigHref(), href);
    }
    
    @Test
    public void noIdentifiers() {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String protocol = (config.getEnvironment() == PROD) ? "https" : "http";
        String href = protocol + "://" + config.getHostnameWithPostfix("ws") 
            + "/v1/assessments/oneGuid/config";
        
        AssessmentReference ref = new AssessmentReference("oneGuid", null, null);
        
        assertEquals(ref.getGuid(), "oneGuid");
        assertNull(ref.getId());
        assertNull(ref.getSharedId());
        assertEquals(ref.getConfigHref(), href);
    }
    
}
