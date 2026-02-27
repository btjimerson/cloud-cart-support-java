package dev.snbv2.cloudcart.support.controller;

import dev.snbv2.cloudcart.support.service.AgentRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller that exposes a health check endpoint for the application.
 *
 * <p>Returns the current health status along with the names of all registered
 * AI agents, which is useful for monitoring and verifying that the system
 * is properly configured.
 */
@RestController
public class HealthController {

    private final AgentRegistry agentRegistry;

    /**
     * Constructs a new {@code HealthController} with the specified agent registry.
     *
     * @param agentRegistry the registry containing all registered AI agents
     */
    public HealthController(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    /**
     * Handles GET requests to {@code /health} and returns the application health status.
     *
     * <p>The response includes a {@code "status"} field indicating the health state
     * and a {@code "registered_agents"} field listing the names of all agents
     * currently registered in the system.
     *
     * @return a map containing the health status and the list of registered agent names
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "registered_agents", agentRegistry.listAgents()
        );
    }
}
