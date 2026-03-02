package dev.snbv2.cloudcart.notifications.config;

import dev.snbv2.cloudcart.notifications.tools.NotificationTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers notification tool methods as MCP-exposed tool callbacks.
 */
@Configuration
public class ToolConfig {

    /**
     * Creates a {@link ToolCallbackProvider} that exposes {@link NotificationTools} methods as MCP tools.
     *
     * @param notificationTools the notification tools service bean
     * @return a provider wrapping the notification tool methods
     */
    @Bean
    public ToolCallbackProvider notificationToolCallbackProvider(NotificationTools notificationTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(notificationTools)
                .build();
    }
}
