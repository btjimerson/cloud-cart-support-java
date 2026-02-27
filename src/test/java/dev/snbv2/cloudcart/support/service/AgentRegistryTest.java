package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.agent.Agent;
import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link AgentRegistry} service.
 * Verifies agent registration, lookup by name, listing of all registered agents,
 * behavior with an empty registry, and overwriting of previously registered agents.
 */
class AgentRegistryTest {

    private AgentRegistry registry;

    /**
     * Initializes a fresh AgentRegistry instance before each test.
     */
    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
    }

    /**
     * Tests that registering an agent and then retrieving it by name returns
     * the same agent instance.
     */
    @Test
    void register_andGet_returnsAgent() {
        Agent mockAgent = new StubAgent("order");
        registry.register("order", mockAgent);
        assertSame(mockAgent, registry.get("order"));
    }

    /**
     * Tests that looking up an agent name that has not been registered throws
     * an IllegalArgumentException with the agent name in the message.
     */
    @Test
    void get_unknownAgent_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.get("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    /**
     * Tests that listAgents returns the names of all registered agents.
     */
    @Test
    void listAgents_returnsAllRegistered() {
        registry.register("order", new StubAgent("order"));
        registry.register("product", new StubAgent("product"));
        registry.register("returns", new StubAgent("returns"));

        Set<String> agents = registry.listAgents();
        assertEquals(3, agents.size());
        assertTrue(agents.containsAll(Set.of("order", "product", "returns")));
    }

    /**
     * Tests that listAgents returns an empty set when no agents have been registered.
     */
    @Test
    void listAgents_emptyRegistry() {
        assertTrue(registry.listAgents().isEmpty());
    }

    /**
     * Tests that registering a second agent with the same name overwrites the
     * first, and subsequent lookups return the second agent.
     */
    @Test
    void register_overwritesExistingAgent() {
        Agent first = new StubAgent("order");
        Agent second = new StubAgent("order");
        registry.register("order", first);
        registry.register("order", second);
        assertSame(second, registry.get("order"));
    }

    /**
     * A minimal Agent implementation used as a test double in registry tests.
     */
    private static class StubAgent implements Agent {
        private final String name;

        /**
         * Creates a new StubAgent with the given name.
         *
         * @param name the agent name
         */
        StubAgent(String name) {
            this.name = name;
        }

        /** {@inheritDoc} */
        @Override
        public String getName() {
            return name;
        }

        /**
         * Returns a stub response without performing any real processing.
         *
         * @param context the conversation context (ignored)
         * @param message the user message (ignored)
         * @return a stub AgentResponse
         */
        @Override
        public AgentResponse handle(ConversationContext context, String message) {
            return new AgentResponse("stub", name);
        }
    }
}
