package dev.snbv2.cloudcart.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the notifications MCP server service.
 *
 * <p>Exposes notification and escalation tools via the Model Context Protocol (MCP),
 * allowing AI agents to send emails, SMS messages, create support tickets,
 * and escalate issues to supervisors.</p>
 */
@SpringBootApplication
public class NotificationsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationsServiceApplication.class, args);
    }
}
