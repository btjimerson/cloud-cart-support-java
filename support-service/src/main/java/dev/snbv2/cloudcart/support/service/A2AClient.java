package dev.snbv2.cloudcart.support.service;

import dev.snbv2.cloudcart.support.model.AgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Client for invoking kagent agents via the A2A (Agent-to-Agent) protocol.
 *
 * <p>Sends tasks to the kagent controller's A2A endpoint and translates
 * the response back into the application's {@link AgentResponse} format.
 */
@Service
@CommonsLog
public class A2AClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String kagentBaseUrl;
    private final String kagentNamespace;

    public A2AClient(ObjectMapper objectMapper,
                     @Value("${kagent.a2a.base-url}") String kagentBaseUrl,
                     @Value("${kagent.a2a.namespace:kagent}") String kagentNamespace) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.kagentBaseUrl = kagentBaseUrl;
        this.kagentNamespace = kagentNamespace;
    }

    /**
     * Invoke a kagent agent via the A2A protocol.
     *
     * @param agentName the name of the kagent Agent CR to invoke
     * @param message   the user's message
     * @param contextId optional conversation/session ID
     * @return an AgentResponse with the agent's reply
     */
    public AgentResponse invoke(String agentName, String message, String contextId) {
        String url = String.format("%s/api/a2a/%s/%s/tasks/send",
                kagentBaseUrl, kagentNamespace, agentName);

        Map<String, Object> taskMessage = new LinkedHashMap<>();
        taskMessage.put("role", "user");
        taskMessage.put("parts", List.of(Map.of("kind", "text", "text", message)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", contextId != null ? contextId : UUID.randomUUID().toString());
        params.put("message", taskMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", UUID.randomUUID().toString());
        requestBody.put("method", "tasks/send");
        requestBody.put("params", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            return parseA2AResponse(response.getBody(), agentName);
        } catch (Exception e) {
            log.error(String.format("A2A invocation failed for agent '%s': %s", agentName, e.getMessage()), e);
            return new AgentResponse(
                    "I apologize, but I encountered an error processing your request. Please try again.",
                    agentName
            );
        }
    }

    @SuppressWarnings("unchecked")
    private AgentResponse parseA2AResponse(Map<String, Object> body, String agentName) {
        if (body == null) {
            return new AgentResponse("No response received from agent.", agentName);
        }

        try {
            // Check for JSON-RPC error
            if (body.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) body.get("error");
                String message = error != null ? String.valueOf(error.get("message")) : "Unknown error";
                log.error(String.format("A2A JSON-RPC error from '%s': %s", agentName, message));
                return new AgentResponse("Agent error: " + message, agentName);
            }

            // Unwrap JSON-RPC envelope: body.result is the A2A task object
            Map<String, Object> task = (Map<String, Object>) body.get("result");
            if (task == null) {
                return new AgentResponse("Agent completed with no result.", agentName);
            }

            // Try history[] for conversation messages
            List<Map<String, Object>> history = (List<Map<String, Object>>) task.get("history");
            if (history != null && !history.isEmpty()) {
                // Get the last agent message
                for (int i = history.size() - 1; i >= 0; i--) {
                    Map<String, Object> msg = history.get(i);
                    if ("agent".equals(msg.get("role"))) {
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) msg.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            String text = (String) parts.get(0).get("text");
                            if (text != null && !text.isBlank()) {
                                return new AgentResponse(text, agentName);
                            }
                        }
                    }
                }
            }

            // Try status.message for final status
            Map<String, Object> status = (Map<String, Object>) task.get("status");
            if (status != null) {
                Map<String, Object> statusMessage = (Map<String, Object>) status.get("message");
                if (statusMessage != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) statusMessage.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        String text = (String) parts.get(0).get("text");
                        if (text != null && !text.isBlank()) {
                            return new AgentResponse(text, agentName);
                        }
                    }
                }
            }

            // Fallback: try artifacts
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) task.get("artifacts");
            if (artifacts != null && !artifacts.isEmpty()) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) artifacts.get(0).get("parts");
                if (parts != null && !parts.isEmpty()) {
                    String text = (String) parts.get(0).get("text");
                    return new AgentResponse(text != null ? text : "Agent completed.", agentName);
                }
            }

            return new AgentResponse("Agent completed.", agentName);
        } catch (Exception e) {
            log.warn(String.format("Failed to parse A2A response: %s", e.getMessage()));
            return new AgentResponse("Agent completed but response could not be parsed.", agentName);
        }
    }
}
