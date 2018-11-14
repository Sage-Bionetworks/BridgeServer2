package org.sagebionetworks.bridge.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * <p>
 * This class hooks into Spring Boot and initializes the Spring application.
 * </p>
 * <p>
 * The main function is needed as the entry point into the Spring application, which calls through to
 * SpringApplication.run(). The annotations on this class also set up Spring component scan and other Spring-based
 * configuration.
 * </p>
 * <p>
 * Running locally will work fine without SpringBootServletInitializer, because mvn spring-boot:run automatically
 * creates a Tomcat container. However, Elastic Beanstalk needs the SpringBootServletInitializer so it can initialize
 * against Elastic Beanstalk's pre-existing Tomcat installation.
 * </p>
 */
@SpringBootApplication
public class AppInitializer extends SpringBootServletInitializer {
    public static void main(String[] args) {
        // Start Spring App and run.
        SpringApplication.run(AppInitializer.class, args);
    }
}
