package dev.snbv2.cloudcart.support.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Specialist agent for handling product-related customer inquiries.
 *
 * <p>This agent handles product searches, recommendations, availability checks, and
 * detailed product information requests.</p>
 *
 * <p>Available tools:</p>
 * <ul>
 *   <li>{@code search_products} - Searches the product catalog by keywords and optional category</li>
 *   <li>{@code get_product_details} - Retrieves detailed information about a specific product</li>
 *   <li>{@code check_availability} - Checks product availability and stock levels</li>
 *   <li>{@code get_recommendations} - Fetches product recommendations, optionally filtered by category</li>
 * </ul>
 */
public class ProductAgent extends BaseToolAgent {

    /** The MCP tool callbacks available to this agent. */
    private final List<ToolCallback> toolCallbacks;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code ProductAgent} with the specified dependencies.
     *
     * @param chatModel     the Anthropic chat model for LLM interactions
     * @param objectMapper  the Jackson ObjectMapper for JSON serialization
     * @param toolCallbacks the MCP tool callbacks available to this agent
     * @param systemPrompt  the system prompt defining this agent's behavior
     */
    public ProductAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                        List<ToolCallback> toolCallbacks, String systemPrompt) {
        super(chatModel, objectMapper);
        this.toolCallbacks = toolCallbacks;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() { return "product"; }

    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }
}
