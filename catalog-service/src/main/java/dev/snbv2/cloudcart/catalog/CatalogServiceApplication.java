package dev.snbv2.cloudcart.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the catalog MCP server service.
 *
 * <p>Exposes product catalog tools via the Model Context Protocol (MCP),
 * allowing AI agents to search products, check availability, and get recommendations.</p>
 */
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
