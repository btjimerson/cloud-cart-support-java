package dev.snbv2.cloudcart.support;

import dev.snbv2.cloudcart.support.config.RequiredEnvChecker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the Agentic Cart service.
 *
 * <p>This class bootstraps the Spring application context, enabling auto-configuration,
 * component scanning, and all other Spring Boot defaults for the agentic shopping cart system.
 */
@SpringBootApplication
public class AgenticCartApplication {

    /**
     * Application main method that launches the Spring Boot application.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AgenticCartApplication.class);
        app.addListeners(new RequiredEnvChecker());
        app.run(args);
    }
}
