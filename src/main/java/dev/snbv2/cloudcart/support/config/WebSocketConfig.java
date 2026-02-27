package dev.snbv2.cloudcart.support.config;

import dev.snbv2.cloudcart.support.controller.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring configuration class that enables and configures WebSocket support.
 *
 * <p>Registers the {@link ChatWebSocketHandler} at the {@code /ws} endpoint with
 * wildcard CORS origins to allow connections from any domain. This enables real-time,
 * bidirectional chat communication between clients and the agentic cart system.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    /**
     * Constructs a new {@code WebSocketConfig} with the specified handler.
     *
     * @param chatWebSocketHandler the WebSocket handler that processes chat messages
     */
    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    /**
     * Registers WebSocket handlers with the given registry.
     *
     * <p>Maps the {@link ChatWebSocketHandler} to the {@code /ws} path and configures
     * it to accept connections from all origins.
     *
     * @param registry the WebSocket handler registry to register handlers with
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .setAllowedOrigins("*");
    }
}
