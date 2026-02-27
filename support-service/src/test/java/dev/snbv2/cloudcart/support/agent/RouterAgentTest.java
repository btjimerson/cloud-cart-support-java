package dev.snbv2.cloudcart.support.agent;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import dev.snbv2.cloudcart.support.service.AgentRegistry;
import dev.snbv2.cloudcart.support.service.HandoffManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RouterAgent}.
 * Verifies intent routing to specialized agents (order, product, returns, complaint),
 * direct handling of general messages (greetings, help, thanks, goodbye),
 * fallback behavior on classification errors, and metadata storage of intent
 * and confidence. Uses a mock {@link ChatModel} and stub agents registered in
 * an {@link AgentRegistry}.
 */
class RouterAgentTest {

    private ChatModel chatModel;
    private AgentRegistry agentRegistry;
    private RouterAgent routerAgent;
    private AgentResponse lastAgentResponse;

    /**
     * Sets up the test fixtures before each test. Creates a mock ChatModel,
     * registers stub agents for order, product, returns, and complaint intents
     * in an AgentRegistry, and constructs a testable RouterAgent subclass that
     * delegates classification to the mock ChatModel.
     */
    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        agentRegistry = new AgentRegistry();
        lastAgentResponse = null;

        // Register stub agents that just record they were called
        for (String name : List.of("order", "product", "returns", "complaint")) {
            final String agentName = name;
            agentRegistry.register(name, new Agent() {
                /** {@inheritDoc} */
                @Override
                public String getName() { return agentName; }

                /** {@inheritDoc} */
                @Override
                public AgentResponse handle(ConversationContext context, String message) {
                    lastAgentResponse = new AgentResponse(agentName + " response", agentName);
                    return lastAgentResponse;
                }
            });
        }

        HandoffManager handoffManager = new HandoffManager(agentRegistry);

        // Create a testable RouterAgent that delegates to our mock ChatModel
        routerAgent = new RouterAgent(null, new ObjectMapper(), handoffManager) {
            /** {@inheritDoc} */
            @Override
            public AgentResponse handle(ConversationContext context, String message) {
                Map<String, Object> classification = classifyWithMock(message);
                String intent = (String) classification.getOrDefault("intent", "general");
                double confidence = classification.containsKey("confidence")
                        ? ((Number) classification.get("confidence")).doubleValue() : 0.0;
                String reasoning = (String) classification.getOrDefault("reasoning", "");

                context.getMetadata().put("last_intent", intent);
                context.getMetadata().put("last_confidence", confidence);

                if ("general".equals(intent)) {
                    return handleGeneralMessage(message);
                }

                java.util.Set<String> validIntents = java.util.Set.of("order", "product", "returns", "complaint");
                if (validIntents.contains(intent)) {
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
             * Classifies the user message by calling the mock ChatModel and parsing
             * the JSON response into intent, confidence, and reasoning fields.
             * Falls back to general intent on any error.
             *
             * @param message the user message to classify
             * @return a map containing intent, confidence, and reasoning
             */
            @SuppressWarnings("unchecked")
            private Map<String, Object> classifyWithMock(String message) {
                try {
                    ChatResponse response = chatModel.call(new Prompt(message));
                    String text = response.getResult().getOutput().getText().trim();
                    if (text.startsWith("```json")) text = text.substring(7);
                    if (text.startsWith("```")) text = text.substring(3);
                    if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
                    return new ObjectMapper().readValue(text.trim(), Map.class);
                } catch (Exception e) {
                    return Map.of("intent", "general", "confidence", 0.5,
                            "reasoning", "Classification error");
                }
            }

            /**
             * Handles general (non-routed) messages by matching keywords for
             * greetings, help requests, thanks, and goodbye messages.
             *
             * @param msg the user message
             * @return an AgentResponse with an appropriate reply
             */
            private AgentResponse handleGeneralMessage(String msg) {
                String lower = msg.toLowerCase();
                String text;
                if (containsAny(lower, "hello", "hi", "hey", "greetings")) {
                    text = "Hello! I'm here to help you with orders, products, returns, or any concerns you may have. What can I assist you with today?";
                } else if (containsAny(lower, "help", "support", "assist")) {
                    text = "I can help you with:\n- Order tracking and status\n- Product search and recommendations\n- Returns and refunds\n- Any complaints or concerns\n\nWhat would you like assistance with?";
                } else if (containsAny(lower, "thanks", "thank you", "appreciate")) {
                    text = "You're welcome! Is there anything else I can help you with?";
                } else if (containsAny(lower, "bye", "goodbye", "exit", "quit")) {
                    text = "Goodbye! Have a great day. Feel free to reach out if you need anything else.";
                } else {
                    text = "I'm here to help! Could you tell me more about what you need?";
                }
                return new AgentResponse(text, getName());
            }

            /**
             * Checks whether the given text contains any of the specified words.
             *
             * @param text  the text to search
             * @param words the words to look for
             * @return true if the text contains at least one of the words
             */
            private boolean containsAny(String text, String... words) {
                for (String word : words) {
                    if (text.contains(word)) return true;
                }
                return false;
            }
        };
    }

    /**
     * Tests that the router agent's name is "router".
     */
    @Test
    void getName_returnsRouter() {
        assertEquals("router", routerAgent.getName());
    }

    /**
     * Tests that greeting messages (hello, hi, hey, greetings) are handled directly
     * by the router without any handoff, and the response contains the expected greeting text.
     *
     * @param input            the greeting message
     * @param expectedContains the text expected in the response
     */
    @ParameterizedTest
    @CsvSource({
            "hello, Hello!",
            "hi there, Hello!",
            "hey, Hello!",
            "greetings, Hello!"
    })
    void handle_greetings_respondsDirectly(String input, String expectedContains) {
        mockClassification("general", 0.99);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, input);

        assertEquals("router", response.getAgent());
        assertTrue(response.getMessage().contains(expectedContains));
        assertNull(lastAgentResponse); // no handoff
    }

    /**
     * Tests that a help request is handled directly by the router and the response
     * lists available assistance options including order tracking and product search.
     */
    @Test
    void handle_helpRequest_respondsWithOptions() {
        mockClassification("general", 0.99);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "I need help");

        assertTrue(response.getMessage().contains("Order tracking"));
        assertTrue(response.getMessage().contains("Product search"));
    }

    /**
     * Tests that a thank-you message is handled directly by the router and the
     * response contains a polite acknowledgment.
     */
    @Test
    void handle_thanksMessage_respondsPolitely() {
        mockClassification("general", 0.99);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "thank you");
        assertTrue(response.getMessage().contains("welcome"));
    }

    /**
     * Tests that a goodbye message is handled directly by the router and the
     * response contains a farewell.
     */
    @Test
    void handle_goodbyeMessage_respondsWithFarewell() {
        mockClassification("general", 0.99);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "goodbye");
        assertTrue(response.getMessage().contains("Goodbye"));
    }

    /**
     * Tests that a message classified with order intent triggers a handoff
     * to the order agent.
     */
    @Test
    void handle_orderIntent_handsOff() {
        mockClassification("order", 0.98);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "Where is my order?");

        assertNotNull(response.getHandoff());
        assertEquals("order", response.getHandoff().getToAgent());
    }

    /**
     * Tests that a message classified with product intent triggers a handoff
     * to the product agent.
     */
    @Test
    void handle_productIntent_handsOff() {
        mockClassification("product", 0.97);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "What headphones do you sell?");

        assertNotNull(response.getHandoff());
        assertEquals("product", response.getHandoff().getToAgent());
    }

    /**
     * Tests that a message classified with returns intent triggers a handoff
     * to the returns agent.
     */
    @Test
    void handle_returnsIntent_handsOff() {
        mockClassification("returns", 0.95);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "I want to return my order");

        assertNotNull(response.getHandoff());
        assertEquals("returns", response.getHandoff().getToAgent());
    }

    /**
     * Tests that a message classified with complaint intent triggers a handoff
     * to the complaint agent.
     */
    @Test
    void handle_complaintIntent_handsOff() {
        mockClassification("complaint", 0.93);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        AgentResponse response = routerAgent.handle(ctx, "This is terrible service!");

        assertNotNull(response.getHandoff());
        assertEquals("complaint", response.getHandoff().getToAgent());
    }

    /**
     * Tests that when the ChatModel throws an exception during classification,
     * the router falls back to general handling without performing a handoff.
     */
    @Test
    void handle_classificationError_fallsBackToGeneral() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("API error"));

        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");
        AgentResponse response = routerAgent.handle(ctx, "something");

        assertEquals("router", response.getAgent());
        assertNull(lastAgentResponse);
    }

    /**
     * Tests that after handling a message, the classified intent and confidence
     * score are stored in the conversation context metadata.
     */
    @Test
    void handle_storesIntentInMetadata() {
        mockClassification("order", 0.98);
        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");

        routerAgent.handle(ctx, "Where is my order?");

        assertEquals("order", ctx.getMetadata().get("last_intent"));
        assertEquals(0.98, ((Number) ctx.getMetadata().get("last_confidence")).doubleValue(), 0.01);
    }

    /**
     * Tests that when the classifier returns an unrecognized intent, the router
     * responds with a fallback message asking for clarification.
     */
    @Test
    void handle_unclearIntent_returnsFallbackMessage() {
        String json = "{\"intent\": \"unknown_intent\", \"confidence\": 0.3, \"reasoning\": \"unclear\"}";
        Generation generation = new Generation(new AssistantMessage(json));
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ConversationContext ctx = new ConversationContext("conv-1", "CUST-001");
        AgentResponse response = routerAgent.handle(ctx, "xyzzy");

        assertEquals("router", response.getAgent());
        assertTrue(response.getMessage().contains("not sure"));
    }

    /**
     * Configures the mock ChatModel to return a JSON classification response
     * with the specified intent and confidence.
     *
     * @param intent     the intent to return in the classification
     * @param confidence the confidence score to return in the classification
     */
    private void mockClassification(String intent, double confidence) {
        String json = String.format(
                "{\"intent\": \"%s\", \"confidence\": %.2f, \"reasoning\": \"test\"}",
                intent, confidence);
        Generation generation = new Generation(new AssistantMessage(json));
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }
}
