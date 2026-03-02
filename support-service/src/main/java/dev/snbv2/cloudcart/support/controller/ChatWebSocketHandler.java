package dev.snbv2.cloudcart.support.controller;

import dev.snbv2.cloudcart.support.agent.RouterAgent;
import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.ContextManager;
import dev.snbv2.cloudcart.support.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket handler that manages real-time chat communication at the {@code /ws} endpoint.
 *
 * <p>This handler implements the same conversation flow as {@link ChatController} but over
 * a WebSocket connection, enabling bidirectional, low-latency messaging. It processes
 * incoming JSON messages, routes to the appropriate agent, and sends JSON responses back
 * through the WebSocket session. Prompt guards are enforced by Agent Gateway.
 */
@Component
@CommonsLog
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ContextManager contextManager;
    private final RateLimitService rateLimitService;
    private final RouterAgent routerAgent;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new {@code ChatWebSocketHandler} with the required dependencies.
     *
     * @param contextManager   the manager responsible for creating and retrieving conversation contexts
     * @param rateLimitService the service that enforces per-client request rate limits
     * @param routerAgent      the router agent that classifies intent and delegates to domain agents
     * @param objectMapper     the Jackson object mapper for serializing and deserializing JSON messages
     */
    public ChatWebSocketHandler(ContextManager contextManager,
                                 RateLimitService rateLimitService, RouterAgent routerAgent,
                                 ObjectMapper objectMapper) {
        this.contextManager = contextManager;
        this.rateLimitService = rateLimitService;
        this.routerAgent = routerAgent;
        this.objectMapper = objectMapper;
    }

    /**
     * Invoked after a new WebSocket connection has been established.
     *
     * <p>Logs the session ID of the newly connected client.
     *
     * @param session the newly established WebSocket session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info(String.format("WebSocket connected: %s", session.getId()));
    }

    /**
     * Handles an incoming text message received over the WebSocket connection.
     *
     * <p>Parses the JSON payload to extract {@code "message"}, {@code "conversation_id"},
     * and {@code "customer_id"} fields, then performs the following steps:
     * <ol>
     *   <li>Validates that the message is present and non-blank</li>
     *   <li>Creates a new conversation or loads an existing one</li>
     *   <li>Records the user's message as a conversation turn</li>
     *   <li>Routes the message to the appropriate domain agent</li>
     *   <li>Sends the agent's JSON response back through the WebSocket session</li>
     * </ol>
     *
     * <p>Prompt guards (PII masking, off-topic rejection) are enforced by Agent Gateway.
     *
     * @param session     the WebSocket session from which the message was received
     * @param textMessage the incoming text message containing a JSON payload
     * @throws Exception if an error occurs during message processing or response serialization
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        Map<String, Object> data = objectMapper.readValue(textMessage.getPayload(), Map.class);

        String message = (String) data.get("message");
        String conversationId = (String) data.get("conversation_id");
        String customerId = (String) data.getOrDefault("customer_id", "");

        if (message == null || message.isBlank()) {
            sendJson(session, Map.of("error", "Message is required"));
            return;
        }

        // Create or load conversation
        ConversationContext ctx;
        if (conversationId != null && !conversationId.isBlank()) {
            ctx = contextManager.get(conversationId);
            if (ctx == null) {
                sendJson(session, Map.of("error", "Conversation not found"));
                return;
            }
        } else {
            ctx = contextManager.create(customerId);
        }

        // Add user turn
        contextManager.addTurn(ctx.getConversationId(), "user", message, "", null);

        // Enforce rate limit
        String rateLimitKey = (customerId instanceof String && !((String) customerId).isBlank())
                ? (String) customerId : ctx.getConversationId();
        if (!rateLimitService.tryAcquire(rateLimitKey)) {
            sendJson(session, Map.of("error", "Rate limit exceeded. Please try again later."));
            return;
        }

        // Route to agent (prompt guards are enforced by Agent Gateway)
        AgentResponse agentResponse = routerAgent.handle(ctx, message);

        // Add assistant turn
        contextManager.addTurn(ctx.getConversationId(), "assistant",
                agentResponse.getMessage(), agentResponse.getAgent(), agentResponse.getToolCalls());

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", agentResponse.getMessage());
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

        response.put("metadata", agentResponse.getMetadata());

        sendJson(session, response);
    }

    /**
     * Invoked after a WebSocket connection has been closed.
     *
     * <p>Logs the session ID and the close status of the disconnected client.
     *
     * @param session the WebSocket session that was closed
     * @param status  the status code and reason for the closure
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info(String.format("WebSocket disconnected: %s (%s)", session.getId(), status));
    }

    /**
     * Handles a transport-level error that occurred on the WebSocket connection.
     *
     * <p>Logs the session ID and the error message for diagnostic purposes.
     *
     * @param session   the WebSocket session on which the error occurred
     * @param exception the exception representing the transport error
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error(String.format("WebSocket error for session %s: %s", session.getId(), exception.getMessage()));
    }

    /**
     * Serializes the given data map to JSON and sends it as a text message through
     * the specified WebSocket session.
     *
     * @param session the WebSocket session to send the message through
     * @param data    the data map to serialize to JSON and send
     * @throws Exception if serialization or message sending fails
     */
    private void sendJson(WebSocketSession session, Map<String, Object> data) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
    }
}
