package dev.snbv2.cloudcart.support.controller;

import dev.snbv2.cloudcart.support.service.ContextManager;
import dev.snbv2.cloudcart.support.model.ConversationContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for the {@link ChatController}.
 * Covers the POST /chat and GET /conversations/{id} endpoints, including
 * automatic conversation creation, 404 responses for invalid conversation IDs,
 * and conversation retrieval with turn history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"spring.ai.anthropic.api-key=test-key"})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContextManager contextManager;

    /**
     * Tests that when no conversation_id is provided, the chat endpoint automatically
     * creates a new conversation and returns its ID in the response.
     */
    @Test
    void chat_missingConversationId_createsNewConversation() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\", \"customer_id\": \"CUST-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").isNotEmpty());
    }

    /**
     * Tests that providing a non-existent conversation_id in the chat request
     * returns a 404 status with an appropriate error message.
     */
    @Test
    void chat_invalidConversationId_returns404() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\", \"conversation_id\": \"nonexistent\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

    /**
     * Tests that GET /conversations/{id} for an existing conversation returns
     * the conversation details including its ID, customer ID, and turn history.
     */
    @Test
    void getConversation_existing_returnsDetails() throws Exception {
        ConversationContext ctx = contextManager.create("CUST-001");
        contextManager.addTurn(ctx.getConversationId(), "user", "test message", "", null);

        mockMvc.perform(get("/conversations/" + ctx.getConversationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").value(ctx.getConversationId()))
                .andExpect(jsonPath("$.customer_id").value("CUST-001"))
                .andExpect(jsonPath("$.turns").isArray())
                .andExpect(jsonPath("$.turns[0].content").value("test message"));
    }

    /**
     * Tests that GET /conversations/{id} for a non-existent conversation ID
     * returns a 404 status with a "Conversation not found" error message.
     */
    @Test
    void getConversation_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/conversations/fake-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

}
