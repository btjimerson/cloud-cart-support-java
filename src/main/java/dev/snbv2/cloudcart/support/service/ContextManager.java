package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.model.AgentHandoff;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.model.Turn;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation state manager that maintains active conversation contexts
 * using a {@link ConcurrentHashMap} for thread-safe access. Provides operations for
 * creating, retrieving, and updating conversation state including turn history,
 * agent handoffs, and agent-specific context filtering.
 */
@Service
public class ContextManager {

    private final ConcurrentHashMap<String, ConversationContext> contexts = new ConcurrentHashMap<>();

    /**
     * Creates a new conversation context for the specified customer and stores it
     * in the in-memory context map. A unique conversation ID is generated via {@link UUID}.
     *
     * @param customerId the identifier of the customer initiating the conversation
     * @return the newly created {@link ConversationContext}
     */
    public ConversationContext create(String customerId) {
        ConversationContext ctx = new ConversationContext(UUID.randomUUID().toString(), customerId);
        contexts.put(ctx.getConversationId(), ctx);
        return ctx;
    }

    /**
     * Retrieves an existing conversation context by its conversation ID.
     *
     * @param conversationId the unique identifier of the conversation
     * @return the {@link ConversationContext} associated with the given ID, or {@code null} if not found
     */
    public ConversationContext get(String conversationId) {
        return contexts.get(conversationId);
    }

    /**
     * Appends a new turn to the conversation identified by the given conversation ID.
     * If the conversation does not exist, the method returns without effect.
     *
     * @param conversationId the unique identifier of the conversation
     * @param role           the role of the participant (e.g., "user", "assistant")
     * @param content        the textual content of the turn
     * @param agent          the name of the agent handling this turn
     * @param toolCalls      a list of tool call maps associated with this turn, may be {@code null}
     */
    public void addTurn(String conversationId, String role, String content,
                        String agent, List<Map<String, Object>> toolCalls) {
        ConversationContext ctx = contexts.get(conversationId);
        if (ctx == null) return;
        ctx.getTurns().add(new Turn(role, content, agent, toolCalls));
    }

    /**
     * Records an agent handoff within a conversation. Builds a summary of the last five
     * turns (truncating content to 100 characters each), creates an {@link AgentHandoff}
     * record, appends it to the conversation's handoff history, and updates the current agent.
     *
     * @param conversationId the unique identifier of the conversation
     * @param fromAgent      the name of the agent handing off
     * @param toAgent        the name of the agent receiving the handoff
     * @param reason         the reason for the handoff
     * @return the created {@link AgentHandoff} record, or {@code null} if the conversation does not exist
     */
    public AgentHandoff handoff(String conversationId, String fromAgent, String toAgent, String reason) {
        ConversationContext ctx = contexts.get(conversationId);
        if (ctx == null) return null;

        List<Turn> recentTurns = ctx.getTurns();
        int start = Math.max(0, recentTurns.size() - 5);
        StringBuilder summary = new StringBuilder();
        for (int i = start; i < recentTurns.size(); i++) {
            Turn t = recentTurns.get(i);
            String content = t.getContent();
            if (content.length() > 100) content = content.substring(0, 100);
            summary.append(t.getRole()).append(": ").append(content);
            if (i < recentTurns.size() - 1) summary.append(" | ");
        }

        AgentHandoff handoffRecord = new AgentHandoff(fromAgent, toAgent, reason, summary.toString());
        ctx.getHandoffs().add(handoffRecord);
        ctx.setCurrentAgent(toAgent);
        return handoffRecord;
    }

    /**
     * Retrieves the subset of conversation turns relevant to a specific agent.
     * Includes turns from the router agent, the specified agent itself, and turns
     * with an empty agent field (e.g., user messages).
     *
     * @param conversationId the unique identifier of the conversation
     * @param agent          the name of the agent whose context to retrieve
     * @return a filtered list of {@link Turn} objects relevant to the agent, or an empty list
     *         if the conversation does not exist
     */
    public List<Turn> getAgentContext(String conversationId, String agent) {
        ConversationContext ctx = contexts.get(conversationId);
        if (ctx == null) return List.of();
        return ctx.getTurns().stream()
                .filter(t -> t.getAgent().equals("router") || t.getAgent().equals(agent) || t.getAgent().isEmpty())
                .toList();
    }
}
