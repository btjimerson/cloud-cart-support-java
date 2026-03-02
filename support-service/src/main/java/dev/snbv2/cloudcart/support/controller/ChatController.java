package dev.snbv2.cloudcart.support.controller;

import dev.snbv2.cloudcart.support.agent.RouterAgent;
import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.ContextManager;
import dev.snbv2.cloudcart.support.service.RateLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller that handles chat interactions with the agentic cart system.
 *
 * <p>Provides endpoints for sending chat messages and retrieving conversation history.
 * Messages are routed to the appropriate domain-specific agent via the {@link RouterAgent}.
 * Prompt guards (PII masking, off-topic rejection) are enforced by Agent Gateway.
 */
@RestController
public class ChatController {

    private final ContextManager contextManager;
    private final RateLimitService rateLimitService;
    private final RouterAgent routerAgent;

    /**
     * Constructs a new {@code ChatController} with the required dependencies.
     *
     * @param contextManager   the manager responsible for creating and retrieving conversation contexts
     * @param rateLimitService the service that enforces per-client request rate limits
     * @param routerAgent      the router agent that classifies intent and delegates to domain agents
     */
    public ChatController(ContextManager contextManager,
                           RateLimitService rateLimitService, RouterAgent routerAgent) {
        this.contextManager = contextManager;
        this.rateLimitService = rateLimitService;
        this.routerAgent = routerAgent;
    }

    /**
     * Handles a chat message submitted via HTTP POST to {@code /chat}.
     *
     * <p>The request body must contain a {@code "message"} field and may optionally include
     * a {@code "conversation_id"} to continue an existing conversation and a {@code "customer_id"}
     * to associate the conversation with a customer. The method performs the following steps:
     * <ol>
     *   <li>Creates a new conversation or loads an existing one based on the conversation ID</li>
     *   <li>Records the user's message as a conversation turn</li>
     *   <li>Routes the message to the appropriate domain agent</li>
     *   <li>Records the agent's response and returns it to the caller</li>
     * </ol>
     *
     * <p>Prompt guards (PII masking, off-topic rejection) are enforced by Agent Gateway
     * before the message reaches the LLM provider.
     *
     * @param request a map containing {@code "message"}, and optionally {@code "conversation_id"}
     *                and {@code "customer_id"}
     * @return a {@link ResponseEntity} containing the agent's response, conversation ID, agent name,
     *         tool calls, and optionally handoff information; or a 404 response if the specified
     *         conversation was not found
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String conversationId = request.get("conversation_id");
        String customerId = request.getOrDefault("customer_id", "");

        // Create or load conversation
        ConversationContext ctx;
        if (conversationId != null && !conversationId.isBlank()) {
            ctx = contextManager.get(conversationId);
            if (ctx == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Conversation not found"));
            }
        } else {
            ctx = contextManager.create(customerId);
        }

        // Add user turn
        contextManager.addTurn(ctx.getConversationId(), "user", message, "", null);

        // Enforce rate limit
        String rateLimitKey = customerId.isBlank() ? ctx.getConversationId() : customerId;
        if (!rateLimitService.tryAcquire(rateLimitKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Please try again later."));
        }

        // Route to agent (prompt guards are enforced by Agent Gateway)
        AgentResponse agentResponse = routerAgent.handle(ctx, message);

        // Add assistant turn
        contextManager.addTurn(ctx.getConversationId(), "assistant",
                agentResponse.getMessage(), agentResponse.getAgent(), agentResponse.getToolCalls());

        // Build response
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("response", agentResponse.getMessage());
        response.put("conversation_id", ctx.getConversationId());
        response.put("agent", agentResponse.getAgent());
        response.put("tool_calls", agentResponse.getToolCalls());
        if (agentResponse.getHandoff() != null) {
            response.put("handoff", Map.of(
                    "from_agent", agentResponse.getHandoff().getFromAgent(),
                    "to_agent", agentResponse.getHandoff().getToAgent(),
                    "reason", agentResponse.getHandoff().getReason()
            ));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the details of an existing conversation by its ID.
     *
     * <p>Returns the full conversation context including conversation ID, customer ID,
     * current agent, conversation turns, handoff history, and creation timestamp.
     *
     * @param conversationId the unique identifier of the conversation to retrieve
     * @return a {@link ResponseEntity} containing the conversation details, or a 404 response
     *         if no conversation with the given ID exists
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable String conversationId) {
        ConversationContext ctx = contextManager.get(conversationId);
        if (ctx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Conversation not found"));
        }

        return ResponseEntity.ok(Map.of(
                "conversation_id", ctx.getConversationId(),
                "customer_id", ctx.getCustomerId(),
                "current_agent", ctx.getCurrentAgent(),
                "turns", ctx.getTurns(),
                "handoffs", ctx.getHandoffs(),
                "created_at", ctx.getCreatedAt()
        ));
    }
}
