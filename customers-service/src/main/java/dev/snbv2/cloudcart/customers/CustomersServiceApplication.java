package dev.snbv2.cloudcart.customers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the customers MCP server service.
 *
 * <p>Exposes customer management tools via the Model Context Protocol (MCP),
 * allowing AI agents to look up customer info, manage loyalty points, add notes,
 * and issue store credits.</p>
 */
@SpringBootApplication
public class CustomersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomersServiceApplication.class, args);
    }
}
