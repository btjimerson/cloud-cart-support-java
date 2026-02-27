package dev.snbv2.cloudcart.support.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory POJO that holds the state of an ongoing conversation.
 *
 * <p>A conversation context tracks the sequence of {@link Turn} objects,
 * any {@link AgentHandoff} events that occurred, the currently active agent,
 * arbitrary metadata, and the conversation's creation timestamp. This object
 * is not persisted via JPA and is maintained in memory for the duration of
 * the conversation session.</p>
 */
@Getter
@Setter
@ToString
public class ConversationContext {

    private String conversationId;
    private String customerId;
    private List<Turn> turns = new ArrayList<>();
    private List<AgentHandoff> handoffs = new ArrayList<>();
    private String currentAgent = "router";
    private Map<String, Object> metadata = new HashMap<>();
    private String createdAt = Instant.now().toString();

    /**
     * Default no-argument constructor.
     */
    public ConversationContext() {}

    /**
     * Constructs a new conversation context with the specified conversation and customer IDs.
     *
     * @param conversationId the unique identifier for this conversation
     * @param customerId the identifier of the customer participating in this conversation
     */
    public ConversationContext(String conversationId, String customerId) {
        this.conversationId = conversationId;
        this.customerId = customerId;
    }
}
