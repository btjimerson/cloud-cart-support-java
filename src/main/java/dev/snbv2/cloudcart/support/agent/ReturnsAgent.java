package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.service.tools.CustomerToolsService;
import dev.snbv2.cloudcart.support.service.tools.OrderToolsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * Specialist agent for handling return and refund-related customer inquiries.
 *
 * <p>This agent handles return requests, return eligibility checks, return initiation,
 * return label generation, and customer information retrieval. It uses both the
 * {@link OrderToolsService} for order and return operations and the {@link CustomerToolsService}
 * for customer account information.</p>
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

    /** Service providing order and return-related tool operations. */
    private final OrderToolsService orderTools;

    /** Service providing customer-related tool operations. */
    private final CustomerToolsService customerTools;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code ReturnsAgent} with the specified dependencies.
     *
     * @param chatModel    the Anthropic chat model for LLM interactions
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @param orderTools   the service providing order and return tool operations
     * @param customerTools the service providing customer-related tool operations
     * @param systemPrompt the system prompt defining this agent's behavior
     */
    public ReturnsAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                        OrderToolsService orderTools, CustomerToolsService customerTools,
                        String systemPrompt) {
        super(chatModel, objectMapper);
        this.orderTools = orderTools;
        this.customerTools = customerTools;
        this.systemPrompt = systemPrompt;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "returns"}
     */
    @Override
    public String getName() { return "returns"; }

    /**
     * {@inheritDoc}
     *
     * @return the returns agent's system prompt
     */
    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    /**
     * Returns the list of tool callbacks available to the returns agent.
     *
     * <p>Provides tools for order status retrieval, return eligibility checking,
     * return initiation, return label generation, and customer information retrieval.</p>
     *
     * @return a list of {@link ToolCallback} instances for return-related tools
     */
    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return List.of(
                createCallback("get_order_status",
                        "Retrieve the current status and details of an order by order ID",
                        """
                        {"type":"object","properties":{"order_id":{"type":"string","description":"The unique order identifier"}},"required":["order_id"]}
                        """,
                        params -> toJson(orderTools.getOrder((String) params.get("order_id")))),
                createCallback("check_return_eligibility",
                        "Check if an order is eligible for return",
                        """
                        {"type":"object","properties":{"order_id":{"type":"string","description":"The unique order identifier"}},"required":["order_id"]}
                        """,
                        params -> toJson(orderTools.checkReturnEligibility((String) params.get("order_id")))),
                createCallback("initiate_return",
                        "Initiate a return for an order",
                        """
                        {"type":"object","properties":{"order_id":{"type":"string","description":"The unique order identifier"},"reason":{"type":"string","description":"Reason for the return"}},"required":["order_id","reason"]}
                        """,
                        params -> toJson(orderTools.initiateReturn(
                                (String) params.get("order_id"),
                                (String) params.get("reason")))),
                createCallback("generate_return_label",
                        "Generate a return shipping label for an order",
                        """
                        {"type":"object","properties":{"order_id":{"type":"string","description":"The unique order identifier"}},"required":["order_id"]}
                        """,
                        params -> toJson(orderTools.generateReturnLabel((String) params.get("order_id")))),
                createCallback("get_customer_info",
                        "Retrieve customer account information",
                        """
                        {"type":"object","properties":{"customer_id":{"type":"string","description":"The customer ID"}},"required":["customer_id"]}
                        """,
                        params -> toJson(customerTools.getCustomer((String) params.get("customer_id"))))
        );
    }
}
