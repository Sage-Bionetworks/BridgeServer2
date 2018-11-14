package org.sagebionetworks.bridge.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import org.sagebionetworks.bridge.heartbeat.HeartbeatLogger;

@ComponentScan("org.sagebionetworks.bridge")
@Configuration
public class SpringConfig {
    private static final String CONFIG_FILE = "BridgeServer2.conf";
    private static final String DEFAULT_CONFIG_FILE = CONFIG_FILE;
    private static final String USER_CONFIG_FILE = System.getProperty("user.home") + "/" + CONFIG_FILE;

    @Bean
    public Config bridgeConfig() {
        String defaultConfig = getClass().getClassLoader().getResource(DEFAULT_CONFIG_FILE).getPath();
        Path defaultConfigPath = Paths.get(defaultConfig);
        Path localConfigPath = Paths.get(USER_CONFIG_FILE);

        try {
            if (Files.exists(localConfigPath)) {
                return new PropertiesConfig(defaultConfigPath, localConfigPath);
            } else {
                return new PropertiesConfig(defaultConfigPath);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Bean
    public HeartbeatLogger heartbeatLogger() {
        HeartbeatLogger heartbeatLogger = new HeartbeatLogger();
        heartbeatLogger.setIntervalMinutes(bridgeConfig().getInt("heartbeat.interval.minutes"));
        return heartbeatLogger;
    }
}
