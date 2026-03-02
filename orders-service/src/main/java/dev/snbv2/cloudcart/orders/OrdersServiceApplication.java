package dev.snbv2.cloudcart.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the orders MCP server service.
 *
 * <p>Exposes order management tools via the Model Context Protocol (MCP),
 * allowing AI agents to query orders, manage cancellations, and process returns.</p>
 */
@SpringBootApplication
public class OrdersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersServiceApplication.class, args);
    }
}
