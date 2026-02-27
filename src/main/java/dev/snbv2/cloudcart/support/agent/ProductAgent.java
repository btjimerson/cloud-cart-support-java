package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.service.tools.ProductToolsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * Specialist agent for handling product-related customer inquiries.
 *
 * <p>This agent handles product searches, recommendations, availability checks, and
 * detailed product information requests. It uses the {@link ProductToolsService} to
 * interact with the product catalog.</p>
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

    /** Service providing product-related tool operations. */
    private final ProductToolsService productTools;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code ProductAgent} with the specified dependencies.
     *
     * @param chatModel    the Anthropic chat model for LLM interactions
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @param productTools the service providing product-related tool operations
     * @param systemPrompt the system prompt defining this agent's behavior
     */
    public ProductAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                        ProductToolsService productTools, String systemPrompt) {
        super(chatModel, objectMapper);
        this.productTools = productTools;
        this.systemPrompt = systemPrompt;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "product"}
     */
    @Override
    public String getName() { return "product"; }

    /**
     * {@inheritDoc}
     *
     * @return the product agent's system prompt
     */
    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    /**
     * Returns the list of tool callbacks available to the product agent.
     *
     * <p>Provides tools for searching products, retrieving product details,
     * checking availability, and getting product recommendations.</p>
     *
     * @return a list of {@link ToolCallback} instances for product-related tools
     */
    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return List.of(
                createCallback("search_products",
                        "Search the product catalog by keywords and optional category",
                        """
                        {"type":"object","properties":{"query":{"type":"string","description":"Search query keywords"},"category":{"type":"string","description":"Optional category to filter by"}},"required":["query"]}
                        """,
                        params -> toJson(productTools.searchProducts(
                                (String) params.get("query"),
                                (String) params.getOrDefault("category", "")))),
                createCallback("get_product_details",
                        "Get detailed information about a specific product by ID",
                        """
                        {"type":"object","properties":{"product_id":{"type":"integer","description":"The product ID"}},"required":["product_id"]}
                        """,
                        params -> toJson(productTools.getProduct(((Number) params.get("product_id")).intValue()))),
                createCallback("check_availability",
                        "Check if a product is available and its stock level",
                        """
                        {"type":"object","properties":{"product_id":{"type":"integer","description":"The product ID"}},"required":["product_id"]}
                        """,
                        params -> toJson(productTools.checkAvailability(((Number) params.get("product_id")).intValue()))),
                createCallback("get_recommendations",
                        "Get product recommendations, optionally filtered by category",
                        """
                        {"type":"object","properties":{"category":{"type":"string","description":"Optional category to filter recommendations"},"limit":{"type":"integer","description":"Maximum number of recommendations (default 3)"}},"required":[]}
                        """,
                        params -> {
                            String category = (String) params.getOrDefault("category", "");
                            int limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : 3;
                            return toJson(productTools.getRecommendations(category, limit));
                        })
        );
    }
}
