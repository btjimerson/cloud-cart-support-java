package dev.snbv2.cloudcart.support.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Specialist agent for handling return and refund-related customer inquiries.
 *
 * <p>This agent handles return requests, return eligibility checks, return initiation,
 * return label generation, and customer information retrieval.</p>
 *
 * <p>Available tools:</p>
 * <ul>
 *   <li>{@code get_order_status} - Retrieves the current status and details of an order</li>
 *   <li>{@code check_return_eligibility} - Checks if an order is eligible for return</li>
 *   <li>{@code initiate_return} - Initiates a return for an order with a specified reason</li>
 *   <li>{@code generate_return_label} - Generates a return shipping label for an order</li>
 *   <li>{@code get_customer_info} - Retrieves customer account information</li>
 * </ul>
 */
public class ReturnsAgent extends BaseToolAgent {

    /** The MCP tool callbacks available to this agent. */
    private final List<ToolCallback> toolCallbacks;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code ReturnsAgent} with the specified dependencies.
     *
     * @param chatModel     the Anthropic chat model for LLM interactions
     * @param objectMapper  the Jackson ObjectMapper for JSON serialization
     * @param toolCallbacks the MCP tool callbacks available to this agent
     * @param systemPrompt  the system prompt defining this agent's behavior
     */
    public ReturnsAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                        List<ToolCallback> toolCallbacks, String systemPrompt) {
        super(chatModel, objectMapper);
        this.toolCallbacks = toolCallbacks;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() { return "returns"; }

    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }
}
