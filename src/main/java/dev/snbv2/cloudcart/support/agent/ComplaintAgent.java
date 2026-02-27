package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.tools.CustomerToolsService;
import dev.snbv2.cloudcart.support.service.tools.NotificationToolsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Specialist agent for handling customer complaints and service escalations.
 *
 * <p>This agent handles customer complaints, dissatisfaction, and service issues. It
 * automatically detects escalation keywords in customer messages and, when found, augments
 * the message with a system note instructing the LLM to create a high-priority support
 * ticket and escalate to a supervisor.</p>
 *
 * <p>Available tools:</p>
 * <ul>
 *   <li>{@code create_support_ticket} - Creates a support ticket for customer issues</li>
 *   <li>{@code get_customer_info} - Retrieves customer account information</li>
 *   <li>{@code issue_credit} - Issues store credit to a customer as compensation</li>
 *   <li>{@code send_email} - Sends an email notification to the customer</li>
 *   <li>{@code escalate_to_supervisor} - Escalates the issue to a supervisor for review</li>
 * </ul>
 */
public class ComplaintAgent extends BaseToolAgent {

    /**
     * Keywords that trigger automatic escalation behavior when detected in a customer message.
     * When any of these keywords are found, the agent augments the message with a system note
     * instructing it to create a high-priority ticket and escalate to a supervisor.
     */
    private static final Set<String> ESCALATION_KEYWORDS = Set.of(
            "lawyer", "lawsuit", "legal action", "attorney",
            "manager", "supervisor", "unacceptable", "outrageous",
            "disgrace", "disgusting", "worst", "terrible service"
    );

    /** Service providing customer-related tool operations. */
    private final CustomerToolsService customerTools;

    /** Service providing notification-related tool operations. */
    private final NotificationToolsService notificationTools;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code ComplaintAgent} with the specified dependencies.
     *
     * @param chatModel         the Anthropic chat model for LLM interactions
     * @param objectMapper      the Jackson ObjectMapper for JSON serialization
     * @param customerTools     the service providing customer-related tool operations
     * @param notificationTools the service providing notification and escalation tool operations
     * @param systemPrompt      the system prompt defining this agent's behavior
     */
    public ComplaintAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                          CustomerToolsService customerTools, NotificationToolsService notificationTools,
                          String systemPrompt) {
        super(chatModel, objectMapper);
        this.customerTools = customerTools;
        this.notificationTools = notificationTools;
        this.systemPrompt = systemPrompt;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "complaint"}
     */
    @Override
    public String getName() { return "complaint"; }

    /**
     * {@inheritDoc}
     *
     * @return the complaint agent's system prompt
     */
    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    /**
     * Handles a customer complaint message, detecting escalation keywords before processing.
     *
     * <p>If the message contains any of the {@link #ESCALATION_KEYWORDS}, the message is
     * augmented with a system note instructing the LLM to create a high-priority support
     * ticket and escalate to a supervisor. The augmented (or original) message is then
     * passed to the parent class's {@link BaseToolAgent#handle} method for tool-assisted processing.</p>
     *
     * @param context the current conversation context
     * @param message the customer's complaint message
     * @return an {@link AgentResponse} containing the agent's reply to the complaint
     */
    @Override
    public AgentResponse handle(ConversationContext context, String message) {
        String lower = message.toLowerCase();
        boolean shouldEscalate = ESCALATION_KEYWORDS.stream().anyMatch(lower::contains);

        if (shouldEscalate) {
            String escalatedMessage = message + "\n\n[SYSTEM NOTE: Escalation keywords detected. " +
                    "Create a HIGH priority support ticket and escalate to supervisor.]";
            return super.handle(context, escalatedMessage);
        }

        return super.handle(context, message);
    }

    /**
     * Returns the list of tool callbacks available to the complaint agent.
     *
     * <p>Provides tools for creating support tickets, retrieving customer information,
     * issuing store credits, sending email notifications, and escalating issues to supervisors.</p>
     *
     * @return a list of {@link ToolCallback} instances for complaint-handling tools
     */
    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return List.of(
                createCallback("create_support_ticket",
                        "Create a support ticket for customer issues",
                        """
                        {"type":"object","properties":{"customer_id":{"type":"string","description":"The customer ID"},"subject":{"type":"string","description":"Ticket subject"},"description":{"type":"string","description":"Detailed description of the issue"},"priority":{"type":"string","description":"Priority level: low, medium, high"}},"required":["customer_id","subject","description"]}
                        """,
                        params -> toJson(notificationTools.createSupportTicket(
                                (String) params.get("customer_id"),
                                (String) params.get("subject"),
                                (String) params.get("description"),
                                (String) params.getOrDefault("priority", "medium")))),
                createCallback("get_customer_info",
                        "Retrieve customer account information",
                        """
                        {"type":"object","properties":{"customer_id":{"type":"string","description":"The customer ID"}},"required":["customer_id"]}
                        """,
                        params -> toJson(customerTools.getCustomer((String) params.get("customer_id")))),
                createCallback("issue_credit",
                        "Issue store credit to a customer as compensation",
                        """
                        {"type":"object","properties":{"customer_id":{"type":"string","description":"The customer ID"},"amount":{"type":"number","description":"Credit amount in dollars"},"reason":{"type":"string","description":"Reason for issuing the credit"}},"required":["customer_id","amount","reason"]}
                        """,
                        params -> toJson(customerTools.issueCredit(
                                (String) params.get("customer_id"),
                                ((Number) params.get("amount")).doubleValue(),
                                (String) params.get("reason")))),
                createCallback("send_email",
                        "Send an email notification to the customer",
                        """
                        {"type":"object","properties":{"to":{"type":"string","description":"Recipient email address"},"subject":{"type":"string","description":"Email subject line"},"body":{"type":"string","description":"Email body content"}},"required":["to","subject","body"]}
                        """,
                        params -> toJson(notificationTools.sendEmail(
                                (String) params.get("to"),
                                (String) params.get("subject"),
                                (String) params.get("body")))),
                createCallback("escalate_to_supervisor",
                        "Escalate the issue to a supervisor for review",
                        """
                        {"type":"object","properties":{"customer_id":{"type":"string","description":"The customer ID"},"reason":{"type":"string","description":"Reason for escalation"}},"required":["customer_id","reason"]}
                        """,
                        params -> toJson(notificationTools.escalateToSupervisor(
                                (String) params.get("customer_id"),
                                (String) params.get("reason"))))
        );
    }
}
