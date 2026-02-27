package dev.snbv2.cloudcart.support.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Specialist agent for handling order-related customer inquiries.
 *
 * <p>This agent handles questions about order status, tracking, cancellations, and shipping.
 * It has access to tools for retrieving order details, cancelling orders, fetching tracking
 * information, and sending notifications (email and SMS) to customers.</p>
 *
 * <p>Available tools:</p>
 * <ul>
 *   <li>{@code get_order_status} - Retrieves the current status and details of an order</li>
 *   <li>{@code cancel_order} - Cancels an order if it has not yet shipped</li>
 *   <li>{@code get_tracking_info} - Fetches shipping tracking information for an order</li>
 *   <li>{@code send_email} - Sends an email notification to the customer</li>
 *   <li>{@code send_sms} - Sends an SMS notification to the customer</li>
 * </ul>
 */
public class OrderAgent extends BaseToolAgent {

    /** The MCP tool callbacks available to this agent. */
    private final List<ToolCallback> toolCallbacks;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code OrderAgent} with the specified dependencies.
     *
     * @param chatModel     the Anthropic chat model for LLM interactions
     * @param objectMapper  the Jackson ObjectMapper for JSON serialization
     * @param toolCallbacks the MCP tool callbacks available to this agent
     * @param systemPrompt  the system prompt defining this agent's behavior
     */
    public OrderAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                      List<ToolCallback> toolCallbacks, String systemPrompt) {
        super(chatModel, objectMapper);
        this.toolCallbacks = toolCallbacks;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() { return "order"; }

    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }
}
