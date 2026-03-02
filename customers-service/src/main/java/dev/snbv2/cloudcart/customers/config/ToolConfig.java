package dev.snbv2.cloudcart.customers.config;

import dev.snbv2.cloudcart.customers.tools.CustomerTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers customer tool methods as MCP-exposed tool callbacks.
 */
@Configuration
public class ToolConfig {

    /**
     * Creates a {@link ToolCallbackProvider} that exposes {@link CustomerTools} methods as MCP tools.
     *
     * @param customerTools the customer tools service bean
     * @return a provider wrapping the customer tool methods
     */
    @Bean
    public ToolCallbackProvider customerToolCallbackProvider(CustomerTools customerTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(customerTools)
                .build();
    }
}
