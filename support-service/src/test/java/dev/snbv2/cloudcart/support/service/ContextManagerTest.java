package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.model.AgentHandoff;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.model.Turn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link ContextManager} service.
 * Covers the full conversation lifecycle including creation, retrieval,
 * turn management, agent handoffs with context summaries, and agent-specific
 * context filtering.
 */
class ContextManagerTest {

    private ContextManager contextManager;

    /**
     * Initializes a fresh ContextManager instance before each test.
     */
    @BeforeEach
    void setUp() {
        contextManager = new ContextManager();
    }

    /**
     * Tests that creating a conversation generates a unique ID, assigns the
     * specified customer, sets the current agent to "router", and starts
     * with empty turns and handoffs.
     */
    @Test
    void create_generatesUniqueConversation() {
        ConversationContext ctx = contextManager.create("CUST-001");
        assertNotNull(ctx.getConversationId());
        assertEquals("CUST-001", ctx.getCustomerId());
        assertEquals("router", ctx.getCurrentAgent());
        assertTrue(ctx.getTurns().isEmpty());
        assertTrue(ctx.getHandoffs().isEmpty());
    }

    /**
     * Tests that creating multiple conversations produces distinct conversation IDs.
     */
    @Test
    void create_multipleConversationsAreIndependent() {
        ConversationContext ctx1 = contextManager.create("CUST-001");
        ConversationContext ctx2 = contextManager.create("CUST-002");
        assertNotEquals(ctx1.getConversationId(), ctx2.getConversationId());
    }

    /**
     * Tests that retrieving an existing conversation by its ID returns the
     * same object that was created.
     */
    @Test
    void get_existingConversation_returnsIt() {
        ConversationContext created = contextManager.create("CUST-001");
        ConversationContext retrieved = contextManager.get(created.getConversationId());
        assertSame(created, retrieved);
    }

    /**
     * Tests that retrieving a non-existent conversation ID returns null.
     */
    @Test
    void get_nonExistent_returnsNull() {
        assertNull(contextManager.get("does-not-exist"));
    }

    /**
     * Tests that adding a turn appends it to the conversation with the
     * correct role and content.
     */
    @Test
    void addTurn_appendsTurnToConversation() {
        ConversationContext ctx = contextManager.create("CUST-001");
        contextManager.addTurn(ctx.getConversationId(), "user", "Hello", "", null);

        assertEquals(1, ctx.getTurns().size());
        Turn turn = ctx.getTurns().get(0);
        assertEquals("user", turn.getRole());
        assertEquals("Hello", turn.getContent());
    }

    /**
     * Tests that adding a turn with a non-existent conversation ID is a no-op
     * and does not throw an exception.
     */
    @Test
    void addTurn_nonExistentConversation_noOp() {
        // Should not throw
        contextManager.addTurn("fake-id", "user", "Hello", "", null);
    }

    /**
     * Tests that turns with different roles (user, assistant) are correctly
     * appended and preserve their role and agent information.
     */
    @Test
    void addTurn_multipleRoles() {
        ConversationContext ctx = contextManager.create("CUST-001");
        contextManager.addTurn(ctx.getConversationId(), "user", "Where is my order?", "", null);
        contextManager.addTurn(ctx.getConversationId(), "assistant", "Let me check.", "order", null);

        assertEquals(2, ctx.getTurns().size());
        assertEquals("user", ctx.getTurns().get(0).getRole());
        assertEquals("assistant", ctx.getTurns().get(1).getRole());
        assertEquals("order", ctx.getTurns().get(1).getAgent());
    }

    /**
     * Tests that performing a handoff records the handoff details, updates the
     * current agent on the conversation context, and stores the handoff in the
     * handoff list.
     */
    @Test
    void handoff_recordsHandoffAndUpdatesAgent() {
        ConversationContext ctx = contextManager.create("CUST-001");
        contextManager.addTurn(ctx.getConversationId(), "user", "I want to return my order", "", null);

        AgentHandoff handoff = contextManager.handoff(ctx.getConversationId(), "router", "returns", "Return request");

        assertNotNull(handoff);
        assertEquals("router", handoff.getFromAgent());
        assertEquals("returns", handoff.getToAgent());
        assertEquals("Return request", handoff.getReason());
        assertEquals("returns", ctx.getCurrentAgent());
        assertEquals(1, ctx.getHandoffs().size());
    }

    /**
     * Tests that performing a handoff on a non-existent conversation returns null.
     */
    @Test
    void handoff_nonExistentConversation_returnsNull() {
        assertNull(contextManager.handoff("fake", "router", "order", "reason"));
    }

    /**
     * Tests that the handoff context summary truncates long turn content
     * to keep the summary shorter than the original content.
     */
    @Test
    void handoff_summaryTruncatesLongContent() {
        ConversationContext ctx = contextManager.create("CUST-001");
        String longContent = "x".repeat(200);
        contextManager.addTurn(ctx.getConversationId(), "user", longContent, "", null);

        AgentHandoff handoff = contextManager.handoff(ctx.getConversationId(), "router", "order", "reason");
        // Summary should truncate content to 100 chars
        assertTrue(handoff.getContextSummary().length() < longContent.length());
    }

    /**
     * Tests that the handoff context summary uses only the last 5 turns,
     * excluding earlier turns from the summary.
     */
    @Test
    void handoff_summaryUsesLast5Turns() {
        ConversationContext ctx = contextManager.create("CUST-001");
        for (int i = 0; i < 10; i++) {
            contextManager.addTurn(ctx.getConversationId(), "user", "msg" + i, "", null);
        }

        AgentHandoff handoff = contextManager.handoff(ctx.getConversationId(), "router", "order", "reason");
        // Summary should contain msg5-msg9 (last 5), not msg0-msg4
        assertTrue(handoff.getContextSummary().contains("msg5"));
        assertTrue(handoff.getContextSummary().contains("msg9"));
        assertFalse(handoff.getContextSummary().contains("msg4"));
    }

    /**
     * Tests that getAgentContext filters turns to include only those belonging
     * to the specified agent and the router agent, excluding turns from other agents.
     */
    @Test
    void getAgentContext_filtersToAgentAndRouterTurns() {
        ConversationContext ctx = contextManager.create("CUST-001");
        contextManager.addTurn(ctx.getConversationId(), "user", "Hello", "router", null);
        contextManager.addTurn(ctx.getConversationId(), "user", "Order question", "order", null);
        contextManager.addTurn(ctx.getConversationId(), "user", "Product question", "product", null);
        contextManager.addTurn(ctx.getConversationId(), "user", "Another order q", "order", null);

        List<Turn> orderContext = contextManager.getAgentContext(ctx.getConversationId(), "order");
        assertEquals(3, orderContext.size()); // router + 2 order turns
    }

    /**
     * Tests that getAgentContext includes turns with an empty agent string,
     * treating them as belonging to the requested agent's context.
     */
    @Test
    void getAgentContext_includesEmptyAgentTurns() {
        ConversationContext ctx = contextManager.create("CUST-001");
        contextManager.addTurn(ctx.getConversationId(), "user", "Hello", "", null);

        List<Turn> context = contextManager.getAgentContext(ctx.getConversationId(), "order");
        assertEquals(1, context.size());
    }

    /**
     * Tests that getAgentContext returns an empty list when the conversation
     * ID does not exist.
     */
    @Test
    void getAgentContext_nonExistentConversation_returnsEmptyList() {
        List<Turn> context = contextManager.getAgentContext("fake", "order");
        assertTrue(context.isEmpty());
    }
}
