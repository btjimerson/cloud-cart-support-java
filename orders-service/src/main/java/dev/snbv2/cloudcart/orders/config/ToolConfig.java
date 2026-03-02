package dev.snbv2.cloudcart.orders.config;

import dev.snbv2.cloudcart.orders.tools.OrderTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers order tool methods as MCP-exposed tool callbacks.
 */
@Configuration
public class ToolConfig {

    /**
     * Creates a {@link ToolCallbackProvider} that exposes {@link OrderTools} methods as MCP tools.
     *
     * @param orderTools the order tools service bean
     * @return a provider wrapping the order tool methods
     */
    @Bean
    public ToolCallbackProvider orderToolCallbackProvider(OrderTools orderTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(orderTools)
                .build();
    }
}
