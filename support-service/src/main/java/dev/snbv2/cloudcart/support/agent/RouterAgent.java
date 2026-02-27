package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.HandoffManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes incoming customer messages to the appropriate specialist agent based on intent classification.
 *
 * <p>The router agent uses the Anthropic chat model to classify each customer message into one of
 * several intent categories (order, product, returns, complaint, or general). For specialist intents,
 * it delegates to the {@link HandoffManager} to transfer the conversation to the appropriate agent.
 * For general intents (greetings, help requests, thanks, goodbyes), it responds directly.</p>
 *
 * <p>This agent does not use tools; instead, it relies on the LLM to produce a structured JSON
 * classification response that is parsed to determine routing.</p>
 */
@CommonsLog
public class RouterAgent implements Agent {

    /** System prompt instructing the LLM to classify customer messages into intent categories. */
    private static final String ROUTER_SYSTEM_PROMPT = """
            You are a routing agent that classifies customer messages and directs them to specialist agents.

            Your job is to analyze the customer's message and determine their primary intent. Then respond with a JSON object.

            Available intents:
            - "order": Questions about order status, tracking, cancellations, shipping
            - "product": Product search, recommendations, availability, specifications
            - "returns": Return requests, refunds, exchange questions, return policy
            - "complaint": Complaints, escalations, service issues, dissatisfaction
            - "general": Greetings, simple questions, chitchat, unclear intent

            Respond ONLY with a JSON object in this exact format:
            {
              "intent": "order|product|returns|complaint|general",
              "confidence": 0.95,
              "reasoning": "Brief explanation of why you chose this intent"
            }

            Examples:
            - "Where is my order?" -> {"intent": "order", "confidence": 0.98, "reasoning": "Customer asking about order location/tracking"}
            - "I want to return this item" -> {"intent": "returns", "confidence": 0.99, "reasoning": "Clear return request"}
            - "What headphones do you have?" -> {"intent": "product", "confidence": 0.97, "reasoning": "Product search query"}
            - "This is unacceptable service!" -> {"intent": "complaint", "confidence": 0.95, "reasoning": "Customer expressing dissatisfaction"}
            - "Hello" -> {"intent": "general", "confidence": 0.99, "reasoning": "Simple greeting"}
            """;

    /** The set of intent values that correspond to specialist agents. */
    private static final Set<String> VALID_INTENTS = Set.of("order", "product", "returns", "complaint");

    /** The Anthropic chat model used for intent classification. */
    private final AnthropicChatModel chatModel;

    /** The Jackson ObjectMapper used for parsing the classification JSON response. */
    private final ObjectMapper objectMapper;

    /** The handoff manager responsible for transferring conversations to specialist agents. */
    private final HandoffManager handoffManager;

    /**
     * Constructs a new {@code RouterAgent} with the specified dependencies.
     *
     * @param chatModel      the Anthropic chat model to use for intent classification
     * @param objectMapper   the Jackson ObjectMapper for parsing classification responses
     * @param handoffManager the handoff manager for transferring conversations to specialist agents
     */
    public RouterAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                       HandoffManager handoffManager) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.handoffManager = handoffManager;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "router"}
     */
    @Override
    public String getName() { return "router"; }

    /**
     * Handles a customer message by classifying its intent and routing to the appropriate agent.
     *
     * <p>The method first classifies the message intent using the LLM, stores the classification
     * metadata in the conversation context, and then either handles the message directly (for
     * general intents) or delegates to the handoff manager (for specialist intents).</p>
     *
     * @param context the current conversation context
     * @param message the customer's message to classify and route
     * @return an {@link AgentResponse} from either this agent (for general messages) or the
     *         specialist agent that the conversation was handed off to
     */
    @Override
    public AgentResponse handle(ConversationContext context, String message) {
        // Classify intent
        Map<String, Object> classification = classifyIntent(message);

        String intent = (String) classification.getOrDefault("intent", "general");
        double confidence = classification.containsKey("confidence")
                ? ((Number) classification.get("confidence")).doubleValue() : 0.0;
        String reasoning = (String) classification.getOrDefault("reasoning", "");

        // Store in metadata
        context.getMetadata().put("last_intent", intent);
        context.getMetadata().put("last_confidence", confidence);

        // Route based on intent
        if ("general".equals(intent)) {
            return handleGeneral(message);
        }

        if (VALID_INTENTS.contains(intent)) {
            String reason = String.format("Intent classified as '%s' (confidence: %.2f). %s",
                    intent, confidence, reasoning);
            return handoffManager.transfer(context, getName(), intent, reason);
        }

        return new AgentResponse(
                "I'm not sure I understood your request. Could you please provide more details?",
                getName()
        );
    }

    /**
     * Classifies the intent of a customer message using the Anthropic chat model.
     *
     * <p>Sends the message to the LLM with a system prompt instructing it to return a JSON
     * object containing the intent, confidence score, and reasoning. The response is parsed
     * from JSON, with markdown code fences stripped if present. If classification fails,
     * returns a default "general" intent with low confidence.</p>
     *
     * @param message the customer's message to classify
     * @return a map containing "intent" (String), "confidence" (Number), and "reasoning" (String)
     */
    private Map<String, Object> classifyIntent(String message) {
        try {
            AnthropicChatOptions options = AnthropicChatOptions.builder()
                    .model("claude-sonnet-4-5-20250929")
                    .maxTokens(500)
                    .build();

            List<Message> messages = List.of(
                    new SystemMessage(ROUTER_SYSTEM_PROMPT),
                    new UserMessage("Classify this message: " + message)
            );

            ChatResponse response = chatModel.call(new Prompt(messages, options));
            String text = response.getResult().getOutput().getText().trim();

            // Strip markdown code fences if present
            if (text.startsWith("```json")) text = text.substring(7);
            if (text.startsWith("```")) text = text.substring(3);
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3);

            return objectMapper.readValue(text.trim(), Map.class);
        } catch (Exception e) {
            log.error(String.format("Classification error: %s", e.getMessage()));
            return Map.of("intent", "general", "confidence", 0.5,
                    "reasoning", "Classification error: " + e.getMessage());
        }
    }

    /**
     * Handles general-intent messages such as greetings, help requests, thanks, and goodbyes.
     *
     * <p>Provides canned responses based on keyword detection in the message. If no specific
     * keywords are matched, returns a generic prompt asking the customer for more details.</p>
     *
     * @param message the customer's general message
     * @return an {@link AgentResponse} with an appropriate conversational reply
     */
    private AgentResponse handleGeneral(String message) {
        String lower = message.toLowerCase();
        String text;

        if (containsAny(lower, "hello", "hi", "hey", "greetings")) {
            text = "Hello! I'm here to help you with orders, products, returns, or any concerns you may have. What can I assist you with today?";
        } else if (containsAny(lower, "help", "support", "assist")) {
            text = """
                    I can help you with:
                    - Order tracking and status
                    - Product search and recommendations
                    - Returns and refunds
                    - Any complaints or concerns

                    What would you like assistance with?""";
        } else if (containsAny(lower, "thanks", "thank you", "appreciate")) {
            text = "You're welcome! Is there anything else I can help you with?";
        } else if (containsAny(lower, "bye", "goodbye", "exit", "quit")) {
            text = "Goodbye! Have a great day. Feel free to reach out if you need anything else.";
        } else {
            text = "I'm here to help! Could you tell me more about what you need? I can assist with orders, products, returns, or any concerns you might have.";
        }

        return new AgentResponse(text, getName());
    }

    /**
     * Checks whether the given text contains any of the specified words.
     *
     * @param text  the text to search within
     * @param words the words to search for
     * @return {@code true} if the text contains at least one of the specified words, {@code false} otherwise
     */
    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
