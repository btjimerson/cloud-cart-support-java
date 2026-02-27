package dev.snbv2.cloudcart.notifications.config;

import dev.snbv2.cloudcart.notifications.tools.NotificationTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider notificationToolCallbackProvider(NotificationTools notificationTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(notificationTools)
                .build();
    }
}
