package dev.snbv2.cloudcart.support.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper POJO for the output produced by an agent.
 *
 * <p>An agent response encapsulates the message text returned by the agent,
 * the agent's name, any tool calls that were executed, an optional
 * {@link AgentHandoff} if the conversation is being transferred, and
 * a metadata map for additional contextual information.</p>
 */
@Getter
@Setter
@ToString
public class AgentResponse {

    private String message;
    private String agent;
    private List<Map<String, Object>> toolCalls = new ArrayList<>();
    private AgentHandoff handoff;
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Default no-argument constructor.
     */
    public AgentResponse() {}

    /**
     * Constructs a new agent response with the specified message and agent name.
     *
     * @param message the response message text
     * @param agent the name of the agent that produced this response
     */
    public AgentResponse(String message, String agent) {
        this.message = message;
        this.agent = agent;
    }
}
