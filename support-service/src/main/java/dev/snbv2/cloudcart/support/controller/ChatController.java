package dev.snbv2.cloudcart.support.controller;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.A2AClient;
import dev.snbv2.cloudcart.support.service.ContextManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller that handles chat interactions.
 *
 * <p>Messages are sent to the kagent router-agent via the A2A protocol.
 * The router-agent delegates to specialist agents (order, product, returns,
 * complaint) which are defined as Kubernetes CRDs.
 */
@RestController
public class ChatController {

    private final ContextManager contextManager;
    private final A2AClient a2aClient;

    public ChatController(ContextManager contextManager, A2AClient a2aClient) {
        this.contextManager = contextManager;
        this.a2aClient = a2aClient;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String conversationId = request.get("conversation_id");
        String customerId = request.getOrDefault("customer_id", "");

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

        contextManager.addTurn(ctx.getConversationId(), "user", message, "", null);

        // Delegate to kagent router-agent via A2A protocol
        AgentResponse agentResponse = a2aClient.invoke("router-agent", message, ctx.getConversationId());

        contextManager.addTurn(ctx.getConversationId(), "assistant",
                agentResponse.getMessage(), agentResponse.getAgent(), agentResponse.getToolCalls());

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("response", agentResponse.getMessage());
        response.put("conversation_id", ctx.getConversationId());
        response.put("agent", agentResponse.getAgent());
        response.put("tool_calls", agentResponse.getToolCalls());

        return ResponseEntity.ok(response);
    }

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
