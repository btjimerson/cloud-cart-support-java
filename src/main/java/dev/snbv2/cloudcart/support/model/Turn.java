package dev.snbv2.cloudcart.support.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-memory POJO representing a single turn within a conversation.
 *
 * <p>A turn captures one exchange in the conversation, including the role
 * of the participant (user, assistant, or system), the message content,
 * the agent that handled the turn, any tool calls that were made, and
 * the timestamp of the turn.</p>
 */
@Getter
@Setter
@ToString
public class Turn {

    private String role; // "user", "assistant", "system"
    private String content;
    private String agent;
    private List<Map<String, Object>> toolCalls = new ArrayList<>();
    private String timestamp = Instant.now().toString();

    /**
     * Default no-argument constructor.
     */
    public Turn() {}

    /**
     * Constructs a new turn with the specified role, content, agent, and tool calls.
     * The timestamp is automatically set to the current time.
     *
     * @param role the role of the participant (e.g., "user", "assistant", "system")
     * @param content the text content of this turn
     * @param agent the name of the agent handling this turn, or {@code null} for an empty string default
     * @param toolCalls the list of tool call maps, or {@code null} for an empty list default
     */
    public Turn(String role, String content, String agent, List<Map<String, Object>> toolCalls) {
        this.role = role;
        this.content = content;
        this.agent = agent != null ? agent : "";
        this.toolCalls = toolCalls != null ? toolCalls : new ArrayList<>();
        this.timestamp = Instant.now().toString();
    }
}
