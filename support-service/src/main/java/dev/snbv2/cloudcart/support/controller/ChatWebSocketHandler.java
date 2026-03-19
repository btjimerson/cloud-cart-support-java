package dev.snbv2.cloudcart.support.controller;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.A2AClient;
import dev.snbv2.cloudcart.support.service.ContextManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket handler for real-time chat at {@code /ws}.
 *
 * <p>Routes messages to the kagent router-agent via the A2A protocol.
 */
@Component
@CommonsLog
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ContextManager contextManager;
    private final A2AClient a2aClient;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ContextManager contextManager, A2AClient a2aClient,
                                 ObjectMapper objectMapper) {
        this.contextManager = contextManager;
        this.a2aClient = a2aClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info(String.format("WebSocket connected: %s", session.getId()));
    }

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

        contextManager.addTurn(ctx.getConversationId(), "user", message, "", null);

        // Delegate to kagent router-agent via A2A protocol
        AgentResponse agentResponse = a2aClient.invoke("router-agent", message, ctx.getConversationId());

        contextManager.addTurn(ctx.getConversationId(), "assistant",
                agentResponse.getMessage(), agentResponse.getAgent(), agentResponse.getToolCalls());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", agentResponse.getMessage());
        response.put("conversation_id", ctx.getConversationId());
        response.put("agent", agentResponse.getAgent());
        response.put("tool_calls", agentResponse.getToolCalls());
        response.put("metadata", agentResponse.getMetadata());

        sendJson(session, response);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info(String.format("WebSocket disconnected: %s (%s)", session.getId(), status));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error(String.format("WebSocket error for session %s: %s", session.getId(), exception.getMessage()));
    }

    private void sendJson(WebSocketSession session, Map<String, Object> data) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
    }
}
