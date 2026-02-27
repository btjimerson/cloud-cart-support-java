package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
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
     */
    private static final Set<String> ESCALATION_KEYWORDS = Set.of(
            "lawyer", "lawsuit", "legal action", "attorney",
            "manager", "supervisor", "unacceptable", "outrageous",
            "disgrace", "disgusting", "worst", "terrible service"
    );

    /** The MCP tool callbacks available to this agent. */
    private final List<ToolCallback> toolCallbacks;

    /** The system prompt defining this agent's persona and behavior. */
    private final String systemPrompt;

    /**
     * Constructs a new {@code ComplaintAgent} with the specified dependencies.
     *
     * @param chatModel     the Anthropic chat model for LLM interactions
     * @param objectMapper  the Jackson ObjectMapper for JSON serialization
     * @param toolCallbacks the MCP tool callbacks available to this agent
     * @param systemPrompt  the system prompt defining this agent's behavior
     */
    public ComplaintAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                          List<ToolCallback> toolCallbacks, String systemPrompt) {
        super(chatModel, objectMapper);
        this.toolCallbacks = toolCallbacks;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() { return "complaint"; }

    @Override
    protected String getSystemPrompt() { return systemPrompt; }

    /**
     * Handles a customer complaint message, detecting escalation keywords before processing.
     *
     * <p>If the message contains any of the {@link #ESCALATION_KEYWORDS}, the message is
     * augmented with a system note instructing the LLM to create a high-priority support
     * ticket and escalate to a supervisor.</p>
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

    @Override
    protected List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }
}
