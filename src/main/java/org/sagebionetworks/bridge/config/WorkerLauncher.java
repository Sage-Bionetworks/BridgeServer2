package org.sagebionetworks.bridge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.heartbeat.HeartbeatLogger;

/**
 * Launches worker threads. This hooks into the Spring Boot command-line runner, which is really just a big
 * Runnable-equivalent that Spring Boot knows about.
 */
@Component("GeneralWorkerLauncher")
public class WorkerLauncher implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(WorkerLauncher.class);

    private HeartbeatLogger heartbeatLogger;

    /** Logs heartbeat at regular intervals to keep the logs alive. */
    @Autowired
    public final void setHeartbeatLogger(HeartbeatLogger heartbeatLogger) {
        this.heartbeatLogger = heartbeatLogger;
    }

    /** Main entry point into the app. Should only be called by Spring Boot. */
    @Override
    public void run(String... args) {
        LOG.info("Starting heartbeat...");
        new Thread(heartbeatLogger).start();
    }
}
