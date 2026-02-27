package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;

/**
 * Contract for all specialist agents in the agentic cart system.
 *
 * <p>Each agent implementation handles a specific domain of customer interactions
 * (e.g., orders, products, returns, complaints) and is identified by a unique name
 * used for routing and handoff purposes.</p>
 */
public interface Agent {

    /**
     * Returns the unique name identifying this agent.
     *
     * <p>The name is used by the router and handoff manager to identify and
     * dispatch messages to the appropriate specialist agent.</p>
     *
     * @return the unique name of this agent (e.g., "order", "product", "returns", "complaint", "router")
     */
    String getName();

    /**
     * Handles a customer message within the given conversation context.
     *
     * @param context the current conversation context, including conversation history and metadata
     * @param message the customer's message to process
     * @return an {@link AgentResponse} containing the agent's reply and any associated metadata
     */
    AgentResponse handle(ConversationContext context, String message);
}
