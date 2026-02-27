package dev.snbv2.cloudcart.support.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * In-memory POJO representing an agent-to-agent handoff during a conversation.
 *
 * <p>When a conversation is transferred from one agent to another, this object
 * records the source agent, the destination agent, the reason for the handoff,
 * a summary of the conversation context at the time of transfer, and the
 * timestamp when the handoff occurred.</p>
 */
@Getter
@Setter
@ToString
public class AgentHandoff {

    private String fromAgent;
    private String toAgent;
    private String reason;
    private String contextSummary;
    private String timestamp = Instant.now().toString();

    /**
     * Default no-argument constructor that records the current timestamp.
     */
    public AgentHandoff() {}

    /**
     * Constructs a new agent handoff with the specified details.
     * The timestamp is automatically set to the current time.
     *
     * @param fromAgent the name of the agent handing off the conversation
     * @param toAgent the name of the agent receiving the conversation
     * @param reason the reason for the handoff
     * @param contextSummary a summary of the conversation context at handoff time
     */
    public AgentHandoff(String fromAgent, String toAgent, String reason, String contextSummary) {
        this.fromAgent = fromAgent;
        this.toAgent = toAgent;
        this.reason = reason;
        this.contextSummary = contextSummary;
    }
}
