package dev.snbv2.cloudcart.support.config;

import dev.snbv2.cloudcart.support.agent.*;
import dev.snbv2.cloudcart.support.service.AgentRegistry;
import dev.snbv2.cloudcart.support.service.HandoffManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring configuration class that defines and wires together all AI agent beans.
 *
 * <p>This configuration creates the router agent, domain-specific agents (order, product,
 * returns, complaint), and the agent registry that tracks all registered agents. Each
 * domain agent is initialized with MCP tool callbacks discovered from remote MCP servers
 * and a system prompt loaded from the classpath.</p>
 */
@Configuration
public class AiConfig {

    /**
     * Creates the {@link RouterAgent} bean responsible for routing user messages
     * to the appropriate domain-specific agent.
     */
    @Bean
    public RouterAgent routerAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                    HandoffManager handoffManager) {
        return new RouterAgent(chatModel, objectMapper, handoffManager);
    }

    /**
     * Creates the {@link OrderAgent} bean that handles order-related inquiries.
     * Tools are sourced from the orders-service and notifications-service MCP servers.
     */
    @Bean
    public OrderAgent orderAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                  ToolCallbackProvider[] toolCallbackProviders) throws IOException {
        String prompt = loadPrompt("prompts/order-agent.txt");
        List<ToolCallback> tools = filterTools(toolCallbackProviders,
                "getOrderStatus", "cancelOrder", "getTrackingInfo", "sendEmail", "sendSms");
        return new OrderAgent(chatModel, objectMapper, tools, prompt);
    }

    /**
     * Creates the {@link ProductAgent} bean that handles product-related inquiries.
     * Tools are sourced from the catalog-service MCP server.
     */
    @Bean
    public ProductAgent productAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                      ToolCallbackProvider[] toolCallbackProviders) throws IOException {
        String prompt = loadPrompt("prompts/product-agent.txt");
        List<ToolCallback> tools = filterTools(toolCallbackProviders,
                "searchProducts", "getProductDetails", "checkAvailability", "getRecommendations");
        return new ProductAgent(chatModel, objectMapper, tools, prompt);
    }

    /**
     * Creates the {@link ReturnsAgent} bean that handles return and refund requests.
     * Tools are sourced from the orders-service and customers-service MCP servers.
     */
    @Bean
    public ReturnsAgent returnsAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                      ToolCallbackProvider[] toolCallbackProviders) throws IOException {
        String prompt = loadPrompt("prompts/returns-agent.txt");
        List<ToolCallback> tools = filterTools(toolCallbackProviders,
                "getOrderStatus", "checkReturnEligibility", "initiateReturn", "generateReturnLabel", "getCustomerInfo");
        return new ReturnsAgent(chatModel, objectMapper, tools, prompt);
    }

    /**
     * Creates the {@link ComplaintAgent} bean that handles customer complaints and escalations.
     * Tools are sourced from the customers-service and notifications-service MCP servers.
     */
    @Bean
    public ComplaintAgent complaintAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                          ToolCallbackProvider[] toolCallbackProviders) throws IOException {
        String prompt = loadPrompt("prompts/complaint-agent.txt");
        List<ToolCallback> tools = filterTools(toolCallbackProviders,
                "createSupportTicket", "getCustomerInfo", "issueCredit", "sendEmail", "escalateToSupervisor");
        return new ComplaintAgent(chatModel, objectMapper, tools, prompt);
    }

    /**
     * Creates the {@link AgentRegistry} bean and registers all domain-specific agents.
     */
    @Bean
    public AgentRegistry agentRegistry(OrderAgent orderAgent, ProductAgent productAgent,
                                        ReturnsAgent returnsAgent, ComplaintAgent complaintAgent) {
        AgentRegistry registry = new AgentRegistry();
        registry.register("order", orderAgent);
        registry.register("product", productAgent);
        registry.register("returns", returnsAgent);
        registry.register("complaint", complaintAgent);
        return registry;
    }

    /**
     * Filters tools from all registered {@link ToolCallbackProvider}s by name.
     *
     * @param providers  the array of tool callback providers (from MCP client auto-config)
     * @param toolNames  the names of tools to include
     * @return a filtered list of {@link ToolCallback} instances matching the given names
     */
    private List<ToolCallback> filterTools(ToolCallbackProvider[] providers, String... toolNames) {
        Set<String> names = Set.of(toolNames);
        return Arrays.stream(providers)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .filter(cb -> names.contains(cb.getToolDefinition().name()))
                .collect(Collectors.toList());
    }

    /**
     * Loads a prompt text file from the classpath.
     *
     * @param path the classpath-relative path to the prompt file
     * @return the contents of the prompt file as a UTF-8 string
     * @throws IOException if the file cannot be found or read
     */
    private String loadPrompt(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
