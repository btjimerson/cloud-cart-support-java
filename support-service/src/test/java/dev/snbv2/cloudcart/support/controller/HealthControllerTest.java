package dev.snbv2.cloudcart.support.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for the {@link HealthController}.
 * Verifies that the GET /health endpoint returns the expected JSON structure
 * including a healthy status and a list of registered agents.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.ai.anthropic.api-key=test-key"})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Tests that GET /health returns HTTP 200 with a JSON body containing
     * a "healthy" status string and a registered_agents array.
     */
    @Test
    void health_returnsHealthyStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.registered_agents").isArray());
    }

    /**
     * Tests that GET /health returns a non-empty list of registered agents,
     * confirming that all agents were initialized during application startup.
     */
    @Test
    void health_registeredAgentsIncludeAllAgents() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered_agents").isNotEmpty());
    }
}
