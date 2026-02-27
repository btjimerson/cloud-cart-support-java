package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.agent.Agent;
import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.model.Turn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link HandoffManager} service.
 * Covers agent transfer mechanics including current agent updates, target agent
 * invocation, handoff message construction with context summaries, graceful
 * handling of empty conversations, summary truncation for long messages, and
 * exception behavior for unknown target agents.
 */
class HandoffManagerTest {

    private AgentRegistry agentRegistry;
    private HandoffManager handoffManager;
    private String lastHandoffMessage;

    /**
     * Sets up the test fixtures before each test. Creates an AgentRegistry with a
     * stub order agent that captures the handoff message, and initializes the
     * HandoffManager.
     */
    @BeforeEach
    void setUp() {
        agentRegistry = new AgentRegistry();
        lastHandoffMessage = null;

        agentRegistry.register("order", new Agent() {
            /** {@inheritDoc} */
            @Override
            public String getName() { return "order"; }

            /**
             * Captures the handoff message for later assertion and returns
             * a stub response.
             *
             * @param context the conversation context
             * @param message the handoff message from the transferring agent
             * @return a stub AgentResponse
             */
            @Override
            public AgentResponse handle(ConversationContext context, String message) {
                lastHandoffMessage = message;
                return new AgentResponse("Order agent response", "order");
            }
        });

        handoffManager = new HandoffManager(agentRegistry);
    }

    /**
     * Tests that after a transfer, the conversation context's current agent
     * is updated to the target agent.
     */
    @Test
    void transfer_updatesCurrentAgent() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        handoffManager.transfer(ctx, "router", "order", "Order inquiry");

        assertEquals("order", ctx.getCurrentAgent());
    }

    /**
     * Tests that the transfer invokes the target agent with a handoff message
     * containing the source agent name, reason, and conversation content.
     */
    @Test
    void transfer_invokesTargetAgent() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");
        ctx.getTurns().add(new Turn("user", "Where is my order?", "router", null));

        handoffManager.transfer(ctx, "router", "order", "Order inquiry");

        assertNotNull(lastHandoffMessage);
        assertTrue(lastHandoffMessage.contains("[HANDOFF from router]"));
        assertTrue(lastHandoffMessage.contains("Order inquiry"));
        assertTrue(lastHandoffMessage.contains("Where is my order?"));
    }

    /**
     * Tests that the transfer returns an AgentResponse with handoff metadata
     * including the source agent, target agent, and reason.
     */
    @Test
    void transfer_returnsResponseWithHandoffInfo() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");
        ctx.getTurns().add(new Turn("user", "Test message", "router", null));

        AgentResponse response = handoffManager.transfer(ctx, "router", "order", "Test reason");

        assertNotNull(response.getHandoff());
        assertEquals("router", response.getHandoff().getFromAgent());
        assertEquals("order", response.getHandoff().getToAgent());
        assertEquals("Test reason", response.getHandoff().getReason());
    }

    /**
     * Tests that transferring with an empty conversation (no turns) is handled
     * gracefully, with the handoff message containing "N/A" for the summary.
     */
    @Test
    void transfer_emptyConversation_handlesGracefully() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = handoffManager.transfer(ctx, "router", "order", "reason");

        assertNotNull(response);
        assertTrue(lastHandoffMessage.contains("N/A"));
    }

    /**
     * Tests that long turn messages are truncated in the handoff context summary,
     * with an ellipsis appended to indicate truncation.
     */
    @Test
    void transfer_summaryTruncatesLongMessages() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");
        String longMessage = "x".repeat(500);
        ctx.getTurns().add(new Turn("user", longMessage, "router", null));

        AgentResponse response = handoffManager.transfer(ctx, "router", "order", "reason");

        String summary = response.getHandoff().getContextSummary();
        assertTrue(summary.length() < longMessage.length());
        assertTrue(summary.contains("..."));
    }

    /**
     * Tests that the handoff summary uses only the last 5 turns from the
     * conversation, excluding earlier turns.
     */
    @Test
    void transfer_summaryUsesLast5Turns() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");
        for (int i = 0; i < 8; i++) {
            ctx.getTurns().add(new Turn("user", "msg" + i, "router", null));
        }

        handoffManager.transfer(ctx, "router", "order", "reason");

        // Handoff message should reference last turns (msg3..msg7) but not early ones (msg0..msg2)
        assertTrue(lastHandoffMessage.contains("msg3"));
        assertTrue(lastHandoffMessage.contains("msg7"));
        assertFalse(lastHandoffMessage.contains("msg2"));
    }

    /**
     * Tests that attempting to transfer to an unregistered agent throws an
     * IllegalArgumentException.
     */
    @Test
    void transfer_unknownAgent_throwsException() {
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        assertThrows(IllegalArgumentException.class,
                () -> handoffManager.transfer(ctx, "router", "unknown", "reason"));
    }
}
