package dev.snbv2.cloudcart.support.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Health check endpoint.
 *
 * <p>Agents are now managed by kagent as Kubernetes CRDs,
 * so this endpoint reports a static list of known agent names.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "agent_runtime", "kagent",
                "registered_agents", List.of("router-agent", "order-agent", "product-agent", "returns-agent", "complaint-agent")
        );
    }
}
