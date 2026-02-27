package dev.snbv2.cloudcart.customers.config;

import dev.snbv2.cloudcart.customers.tools.CustomerTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider customerToolCallbackProvider(CustomerTools customerTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(customerTools)
                .build();
    }
}
