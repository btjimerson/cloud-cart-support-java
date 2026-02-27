package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.service.tools.NotificationToolsService;
import dev.snbv2.cloudcart.support.service.tools.OrderToolsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

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

    /** Service providing order-related tool operations. */
    private final OrderToolsService orderTools;

    /** Service providing notification-related tool operations. */
    private final NotificationToolsService notificationTools;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code OrderAgent} with the specified dependencies.
     *
     * @param chatModel         the Anthropic chat model for LLM interactions
     * @param objectMapper      the Jackson ObjectMapper for JSON serialization
     * @param orderTools        the service providing order-related tool operations
     * @param notificationTools the service providing notification tool operations (email, SMS)
     * @param systemPrompt      the system prompt defining this agent's behavior
     */
    public OrderAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                      OrderToolsService orderTools, NotificationToolsService notificationTools,
                      String systemPrompt) {
        super(chatModel, objectMapper);
        this.orderTools = orderTools;
        this.notificationTools = notificationTools;
        this.systemPrompt = systemPrompt;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "order"}
     */
    @Override
    public String getName() { return "order"; }

    /**
     * {@inheritDoc}
     *
     * @return the order agent's system prompt
     */
    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    /**
     * Returns the list of tool callbacks available to the order agent.
     *
     * <p>Provides tools for order status retrieval, order cancellation, tracking information,
     * email notifications, and SMS notifications.</p>
     *
     * @return a list of {@link ToolCallback} instances for order-related tools
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
                createCallback("cancel_order",
                        "Cancel an order if it hasn't shipped yet",
                        """
                        {"type":"object","properties":{"order_id":{"type":"string","description":"The unique order identifier"}},"required":["order_id"]}
                        """,
                        params -> toJson(orderTools.cancelOrder((String) params.get("order_id")))),
                createCallback("get_tracking_info",
                        "Get shipping tracking information for an order",
                        """
                        {"type":"object","properties":{"order_id":{"type":"string","description":"The unique order identifier"}},"required":["order_id"]}
                        """,
                        params -> toJson(orderTools.trackShipment((String) params.get("order_id")))),
                createCallback("send_email",
                        "Send an email notification to the customer",
                        """
                        {"type":"object","properties":{"to":{"type":"string","description":"Recipient email address"},"subject":{"type":"string","description":"Email subject line"},"body":{"type":"string","description":"Email body content"}},"required":["to","subject","body"]}
                        """,
                        params -> toJson(notificationTools.sendEmail(
                                (String) params.get("to"),
                                (String) params.get("subject"),
                                (String) params.get("body")))),
                createCallback("send_sms",
                        "Send an SMS notification to the customer",
                        """
                        {"type":"object","properties":{"to":{"type":"string","description":"Recipient phone number"},"message":{"type":"string","description":"SMS message content"}},"required":["to","message"]}
                        """,
                        params -> toJson(notificationTools.sendSms(
                                (String) params.get("to"),
                                (String) params.get("message"))))
        );
    }
}
