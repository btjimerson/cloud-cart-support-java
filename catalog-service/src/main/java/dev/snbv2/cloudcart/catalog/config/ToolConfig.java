package dev.snbv2.cloudcart.catalog.config;

import dev.snbv2.cloudcart.catalog.tools.CatalogTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers catalog tool methods as MCP-exposed tool callbacks.
 */
@Configuration
public class ToolConfig {

    /**
     * Creates a {@link ToolCallbackProvider} that exposes {@link CatalogTools} methods as MCP tools.
     *
     * @param catalogTools the catalog tools service bean
     * @return a provider wrapping the catalog tool methods
     */
    @Bean
    public ToolCallbackProvider catalogToolCallbackProvider(CatalogTools catalogTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(catalogTools)
                .build();
    }
}
