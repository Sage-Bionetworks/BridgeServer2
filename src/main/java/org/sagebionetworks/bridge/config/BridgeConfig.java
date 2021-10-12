package org.sagebionetworks.bridge.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class BridgeConfig implements Config {

    private static final String CONFIG_FILE = "BridgeServer2.conf";
    private static final String DEFAULT_CONFIG_FILE = CONFIG_FILE;
    private static final String USER_CONFIG_FILE = System.getProperty("user.home") + "/" + CONFIG_FILE;

    private static final String CONSENTS_BUCKET = "consents.bucket";

    // Property for a token that is checked before user is unsubscribed from further emails
    private static final String EMAIL_UNSUBSCRIBE_TOKEN = "email.unsubscribe.token";

    private static final String HOST_POSTFIX = "host.postfix";

    private static final String WEBSERVICES_URL = "webservices.url";

    private static final String EXPORTER_SYNAPSE_ID = "exporter.synapse.id";

    private static final String USE_HTTPS_FORWARDING = "use.https.forwarding";

    private final Config config;

    BridgeConfig() {
        String defaultConfig = getClass().getClassLoader().getResource(DEFAULT_CONFIG_FILE).getPath();
        Path defaultConfigPath = Paths.get(defaultConfig);
        Path localConfigPath = Paths.get(USER_CONFIG_FILE);

        try {
            if (Files.exists(localConfigPath)) {
                config = new PropertiesConfig(defaultConfigPath, localConfigPath);
            } else {
                config = new PropertiesConfig(defaultConfigPath);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String getUser() {
        return config.getUser();
    }

    @Override
    public Environment getEnvironment() {
        return config.getEnvironment();
    }

    @Override
    public String get(String key) {
        return config.get(key);
    }

    @Override
    public int getInt(String key) {
        return config.getInt(key);
    }

    @Override
    public List<String> getList(String key) {
        return config.getList(key);
    }

    public boolean isLocal() {
        return Environment.LOCAL.equals(getEnvironment());
    }

    public boolean isDevelopment() {
        return Environment.DEV.equals(getEnvironment());
    }

    public boolean isProduction() {
        return Environment.PROD.equals(getEnvironment());
    }

    public String getEmailUnsubscribeToken() {
        return getProperty(EMAIL_UNSUBSCRIBE_TOKEN);
    }

    public String getProperty(String name) {
        return config.get(name);
    }

    public int getPropertyAsInt(String name) {
        return config.getInt(name);
    }

    public List<String> getPropertyAsList(String name) {
        return config.getList(name);
    }

    public String getConsentsBucket() {
        return config.get(CONSENTS_BUCKET);
    }

    public String getWebservicesURL() {
        return config.get(WEBSERVICES_URL);
    }

    public String getHostnameWithPostfix(String identifier) {
        checkNotNull(identifier);
        return identifier + config.get(HOST_POSTFIX);
    }

    public String getExporterSynapseId() {
        return config.get(EXPORTER_SYNAPSE_ID);
    }

    public boolean useHttpsForwarding() {
        return Boolean.parseBoolean(config.get(USE_HTTPS_FORWARDING));
    }
}
