package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.model.Turn;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.*;
import java.util.function.Function;

/**
 * Abstract base class for agents that use tools via Spring AI's Anthropic ChatModel.
 *
 * <p>This class implements the {@link Agent} interface and provides the core tool-calling
 * loop powered by Spring AI's built-in tool calling support. Subclasses define their
 * domain-specific system prompt and tool callbacks, while this class manages the
 * conversation history construction, chat model invocation, and iterative tool execution.</p>
 *
 * <p>The tool-calling loop runs for up to {@value #MAX_ITERATIONS} iterations, allowing
 * the model to invoke multiple tools sequentially before producing a final text response.</p>
 */
@CommonsLog
public abstract class BaseToolAgent implements Agent {

    /** Maximum number of tool-calling iterations before returning a fallback response. */
    private static final int MAX_ITERATIONS = 5;

    /** The Anthropic chat model used for generating responses and invoking tools. */
    protected final AnthropicChatModel chatModel;

    /** The Jackson ObjectMapper used for JSON serialization of tool results. */
    protected final ObjectMapper objectMapper;

    /**
     * Constructs a new {@code BaseToolAgent} with the specified chat model and object mapper.
     *
     * @param chatModel    the Anthropic chat model to use for LLM interactions
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     */
    protected BaseToolAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the system prompt that defines this agent's persona and behavior.
     *
     * <p>Subclasses must provide a domain-specific system prompt that instructs the
     * model on how to behave and what tools are available.</p>
     *
     * @return the system prompt string for this agent
     */
    protected abstract String getSystemPrompt();

    /**
     * Returns the list of tool callbacks available to this agent.
     *
     * <p>Each callback defines a tool that the LLM can invoke during the conversation,
     * including its name, description, input schema, and handler function.</p>
     *
     * @return a list of {@link ToolCallback} instances representing the agent's tools
     */
    protected abstract List<ToolCallback> getToolCallbacks();

    /**
     * Handles a customer message by building the conversation history, configuring
     * tool callbacks, and running the tool-calling loop.
     *
     * <p>This method reconstructs the message history from the conversation context,
     * appends the current user message, and iteratively calls the chat model. The loop
     * continues until the model produces a text response (rather than a tool call) or
     * the maximum iteration count is reached.</p>
     *
     * @param context the current conversation context containing prior turns and metadata
     * @param message the customer's message to process
     * @return an {@link AgentResponse} containing the agent's reply, tool call records, and metadata
     */
    @Override
    public AgentResponse handle(ConversationContext context, String message) {
        List<Message> messages = new ArrayList<>();

        for (Turn turn : context.getTurns()) {
            if ("user".equals(turn.getRole())) {
                messages.add(new UserMessage(turn.getContent()));
            } else if ("assistant".equals(turn.getRole())) {
                messages.add(new AssistantMessage(turn.getContent()));
            }
        }

        messages.add(new UserMessage(message));

        List<ToolCallback> callbacks = getToolCallbacks();
        List<String> toolNames = callbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .toolCallbacks(callbacks)
                .toolNames(new HashSet<>(toolNames))
                .build();

        List<Map<String, Object>> allToolCalls = new ArrayList<>();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            try {
                List<Message> fullMessages = new ArrayList<>();
                fullMessages.add(new SystemMessage(getSystemPrompt()));
                fullMessages.addAll(messages);

                Prompt fullPrompt = new Prompt(fullMessages, options);
                ChatResponse response = chatModel.call(fullPrompt);

                String content = response.getResult().getOutput().getText();

                if (content != null && !content.isBlank()) {
                    AgentResponse agentResponse = new AgentResponse(content, getName());
                    agentResponse.setToolCalls(allToolCalls);
                    agentResponse.getMetadata().put("iterations", iteration + 1);
                    return agentResponse;
                }
            } catch (Exception e) {
                log.error(String.format("Error in agent %s iteration %d: %s", getName(), iteration, e.getMessage()), e);
                return new AgentResponse(
                        "I apologize, but I encountered an error processing your request. Please try again.",
                        getName()
                );
            }
        }

        AgentResponse response = new AgentResponse(
                "I apologize, but I'm having trouble completing your request. Please try again or contact support.",
                getName()
        );
        response.setToolCalls(allToolCalls);
        return response;
    }

    /**
     * Creates a {@link ToolCallback} that the LLM can invoke as a tool during conversation.
     *
     * <p>This is a convenience method for constructing tool callbacks with a consistent
     * pattern. The handler function receives the parsed input parameters as a map and
     * returns a JSON string result.</p>
     *
     * @param name        the unique name of the tool (e.g., "get_order_status")
     * @param description a human-readable description of what the tool does
     * @param inputSchema the JSON Schema defining the tool's expected input parameters
     * @param handler     a function that processes the input parameters and returns a JSON string result
     * @return a configured {@link ToolCallback} ready for use with the chat model
     */
    protected ToolCallback createCallback(String name, String description,
                                               String inputSchema,
                                               Function<Map<String, Object>, String> handler) {
        return FunctionToolCallback.builder(name, handler)
                .description(description)
                .inputType(Map.class)
                .inputSchema(inputSchema)
                .build();
    }

    /**
     * Serializes the given object to a JSON string.
     *
     * <p>If serialization fails, returns a JSON error object instead of throwing an exception.</p>
     *
     * @param obj the object to serialize
     * @return the JSON string representation of the object, or a JSON error object if serialization fails
     */
    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\": \"serialization error\"}";
        }
    }
}
