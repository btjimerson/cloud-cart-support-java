package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.model.AgentHandoff;
import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.model.Turn;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Manages agent-to-agent transfers (handoffs) during a conversation. Coordinates the
 * transition from one agent to another by building a handoff message that includes the
 * transfer reason, a summary of recent conversation history, and the user's last message.
 * The target agent is resolved via the {@link AgentRegistry} and invoked to continue
 * handling the conversation.
 */
@Service
public class HandoffManager {

    private final AgentRegistry agentRegistry;

    /**
     * Constructs a new {@code HandoffManager} with the specified agent registry.
     *
     * @param agentRegistry the registry used to look up target agents by name
     */
    public HandoffManager(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    /**
     * Transfers a conversation from one agent to another. Updates the current agent on the
     * conversation context, builds a handoff message containing the reason, a summary of
     * recent conversation turns, and the user's last message, then invokes the target agent
     * to handle the conversation from that point.
     *
     * @param context   the current conversation context
     * @param fromAgent the name of the agent initiating the transfer
     * @param toAgent   the name of the agent receiving the transfer
     * @param reason    the reason for the transfer
     * @return the {@link AgentResponse} from the target agent, with handoff metadata attached
     */
    public AgentResponse transfer(ConversationContext context, String fromAgent,
                                   String toAgent, String reason) {
        // Record handoff in metadata
        context.setCurrentAgent(toAgent);

        // Create conversation summary
        String summary = summarizeConversation(context);

        // Build handoff message
        String handoffMessage = String.format("""
                [HANDOFF from %s]
                Reason: %s

                Recent conversation summary:
                %s

                User's last message: %s

                Please assist the user from here.""",
                fromAgent, reason, summary,
                !context.getTurns().isEmpty()
                        ? context.getTurns().get(context.getTurns().size() - 1).getContent()
                        : "N/A");

        // Get target agent and invoke
        var targetAgent = agentRegistry.get(toAgent);
        AgentResponse response = targetAgent.handle(context, handoffMessage);

        // Attach handoff info
        response.setHandoff(new AgentHandoff(fromAgent, toAgent, reason, summary));

        return response;
    }

    /**
     * Builds a summary of the conversation from the last five turns. Each turn's content
     * is truncated to 200 characters, and roles are displayed as "User" or "Agent".
     * Returns a descriptive message if the conversation has no turns.
     *
     * @param context the conversation context containing the turn history
     * @return a string summary of the most recent conversation turns
     */
    private String summarizeConversation(ConversationContext context) {
        List<Turn> turns = context.getTurns();
        if (turns.isEmpty()) return "No prior conversation.";

        int start = Math.max(0, turns.size() - 5);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            String role = "user".equals(turn.getRole()) ? "User" : "Agent";
            String content = turn.getContent();
            if (content.length() > 200) content = content.substring(0, 200) + "...";
            sb.append(role).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }
}
