package org.sagebionetworks.bridge.models.appconfig;

import static org.testng.Assert.assertEquals;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;

public class ConfigResolverTest extends Mockito {
    
    @Mock
    BridgeConfig mockConfig;
    
    ConfigResolver resolver;
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
        resolver = new ConfigResolver(mockConfig);
    }
    
    @Test
    public void testWithHttps() {
        when(mockConfig.getEnvironment()).thenReturn(Environment.UAT);
        when(mockConfig.getHostnameWithPostfix("sub")).thenReturn("sub.bridge.org");
        
        String result = resolver.url("sub", "/v2/path");
        assertEquals(result, "https://sub.bridge.org/v2/path");
    }

    @Test
    public void testWithHttp() {
        when(mockConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        when(mockConfig.getHostnameWithPostfix("sub2")).thenReturn("sub2.bridge.org");
        
        String result = resolver.url("sub2", "/v2/path");
        assertEquals(result, "http://sub2.bridge.org/v2/path");
    }
}
