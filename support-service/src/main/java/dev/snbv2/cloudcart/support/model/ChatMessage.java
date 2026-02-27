package dev.snbv2.cloudcart.support.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * Data transfer object used for WebSocket communication, serving as both
 * the inbound request and outbound response message format.
 *
 * <p>Request fields include the user's message text, conversation ID, and
 * customer ID. Response fields include the agent's content, agent name,
 * tool calls, handoff information, error details, and metadata.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ChatMessage {

    private String message;
    private String conversationId;
    private String customerId;

    // Response fields
    private String content;
    private String agent;
    private List<Map<String, Object>> toolCalls;
    private AgentHandoff handoff;
    private String error;
    private Map<String, Object> metadata;
}
