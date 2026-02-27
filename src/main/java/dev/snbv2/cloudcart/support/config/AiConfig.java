package dev.snbv2.cloudcart.support.config;

import dev.snbv2.cloudcart.support.agent.*;
import dev.snbv2.cloudcart.support.service.AgentRegistry;
import dev.snbv2.cloudcart.support.service.HandoffManager;
import dev.snbv2.cloudcart.support.service.tools.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring configuration class that defines and wires together all AI agent beans.
 *
 * <p>This configuration creates the router agent, domain-specific agents (order, product,
 * returns, complaint), and the agent registry that tracks all registered agents. Each
 * domain agent is initialized with a system prompt loaded from the classpath.
 */
@Configuration
public class AiConfig {

    /**
     * Creates the {@link RouterAgent} bean responsible for routing user messages
     * to the appropriate domain-specific agent.
     *
     * @param chatModel      the Anthropic chat model used for intent classification
     * @param objectMapper   the Jackson object mapper for JSON processing
     * @param handoffManager the handoff manager for transferring conversations between agents
     * @return a configured {@link RouterAgent} instance
     */
    @Bean
    public RouterAgent routerAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                    HandoffManager handoffManager) {
        return new RouterAgent(chatModel, objectMapper, handoffManager);
    }

    /**
     * Creates the {@link OrderAgent} bean that handles order-related inquiries such as
     * order status, tracking, and modifications.
     *
     * @param chatModel         the Anthropic chat model used for generating responses
     * @param objectMapper      the Jackson object mapper for JSON processing
     * @param orderTools        the service providing order-related tool functions
     * @param notificationTools the service providing notification-related tool functions
     * @return a configured {@link OrderAgent} instance
     * @throws IOException if the order agent prompt file cannot be read from the classpath
     */
    @Bean
    public OrderAgent orderAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                  OrderToolsService orderTools, NotificationToolsService notificationTools) throws IOException {
        String prompt = loadPrompt("prompts/order-agent.txt");
        return new OrderAgent(chatModel, objectMapper, orderTools, notificationTools, prompt);
    }

    /**
     * Creates the {@link ProductAgent} bean that handles product-related inquiries such as
     * product search, details, and availability.
     *
     * @param chatModel    the Anthropic chat model used for generating responses
     * @param objectMapper the Jackson object mapper for JSON processing
     * @param productTools the service providing product-related tool functions
     * @return a configured {@link ProductAgent} instance
     * @throws IOException if the product agent prompt file cannot be read from the classpath
     */
    @Bean
    public ProductAgent productAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                      ProductToolsService productTools) throws IOException {
        String prompt = loadPrompt("prompts/product-agent.txt");
        return new ProductAgent(chatModel, objectMapper, productTools, prompt);
    }

    /**
     * Creates the {@link ReturnsAgent} bean that handles return and refund requests.
     *
     * @param chatModel     the Anthropic chat model used for generating responses
     * @param objectMapper  the Jackson object mapper for JSON processing
     * @param orderTools    the service providing order-related tool functions
     * @param customerTools the service providing customer-related tool functions
     * @return a configured {@link ReturnsAgent} instance
     * @throws IOException if the returns agent prompt file cannot be read from the classpath
     */
    @Bean
    public ReturnsAgent returnsAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                      OrderToolsService orderTools, CustomerToolsService customerTools) throws IOException {
        String prompt = loadPrompt("prompts/returns-agent.txt");
        return new ReturnsAgent(chatModel, objectMapper, orderTools, customerTools, prompt);
    }

    /**
     * Creates the {@link ComplaintAgent} bean that handles customer complaints and escalations.
     *
     * @param chatModel         the Anthropic chat model used for generating responses
     * @param objectMapper      the Jackson object mapper for JSON processing
     * @param customerTools     the service providing customer-related tool functions
     * @param notificationTools the service providing notification-related tool functions
     * @return a configured {@link ComplaintAgent} instance
     * @throws IOException if the complaint agent prompt file cannot be read from the classpath
     */
    @Bean
    public ComplaintAgent complaintAgent(AnthropicChatModel chatModel, ObjectMapper objectMapper,
                                          CustomerToolsService customerTools, NotificationToolsService notificationTools) throws IOException {
        String prompt = loadPrompt("prompts/complaint-agent.txt");
        return new ComplaintAgent(chatModel, objectMapper, customerTools, notificationTools, prompt);
    }

    /**
     * Creates the {@link AgentRegistry} bean and registers all domain-specific agents
     * under their respective names.
     *
     * @param orderAgent    the order agent to register
     * @param productAgent  the product agent to register
     * @param returnsAgent  the returns agent to register
     * @param complaintAgent the complaint agent to register
     * @return a configured {@link AgentRegistry} containing all registered agents
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
     * Loads a prompt text file from the classpath.
     *
     * @param path the classpath-relative path to the prompt file (e.g., "prompts/order-agent.txt")
     * @return the contents of the prompt file as a UTF-8 string
     * @throws IOException if the file cannot be found or read
     */
    private String loadPrompt(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
