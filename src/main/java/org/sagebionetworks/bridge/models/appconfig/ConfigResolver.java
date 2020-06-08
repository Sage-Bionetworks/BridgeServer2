package org.sagebionetworks.bridge.models.appconfig;

import static org.sagebionetworks.bridge.config.Environment.LOCAL;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

/**
 * The static address of BridgeConfig makes testing difficult. This class 
 * combines a utility method to create an environment-specific URL with 
 * a means to inject a mock for testing. Reference objects in the AppConfig
 * have been refactored to call out to this resolver to include URLs
 * for resources.
 */
public class ConfigResolver {

    /** The default instance of the resolver, used for deserialized instances of
     * references and references that are not constructed with a resolver.
     */
    public static final ConfigResolver INSTANCE = new ConfigResolver();
    
    private BridgeConfig config;
    
    public ConfigResolver() {
        this.config = BridgeConfigFactory.getConfig();
    }
    
    /** Constructor to in mock bridge config. */
    public ConfigResolver(BridgeConfig config) {
        this.config = config;
    }
    
    /**
     * Return an URL with the correct subdomain for the *.sagebridge.org domain, 
     * using the correct protocol. 
     */
    public String url(String subdomain, String path) {
        Environment env = config.getEnvironment();
        String baseUrl = config.getHostnameWithPostfix(subdomain);
        String protocol = (env == LOCAL) ? "http" : "https";
        return String.format("%s://%s%s", protocol, baseUrl, path);
    }
}
