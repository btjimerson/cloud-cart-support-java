package dev.snbv2.cloudcart.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test for the Spring Boot application context.
 * Verifies that the application context loads successfully with all beans
 * and configuration wired correctly.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.anthropic.api-key=test-key"
})
class AgenticCartApplicationTests {

    /**
     * Tests that the Spring application context loads without errors.
     * A failure here indicates a configuration or wiring problem in the application.
     */
    @Test
    void contextLoads() {
    }
}
