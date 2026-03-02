package dev.snbv2.cloudcart.orders.config;

import dev.snbv2.cloudcart.orders.model.*;
import dev.snbv2.cloudcart.orders.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Seeds the orders database with initial data on application startup.
 *
 * <p>Reads order definitions from {@code seed-data/orders.json} on the classpath,
 * including order items, and persists them via {@link OrderRepository}.</p>
 */
@Component
@CommonsLog
public class DataSeeder implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public DataSeeder(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        try (InputStream is = new ClassPathResource("seed-data/orders.json").getInputStream()) {
            List<JsonNode> orders = objectMapper.readValue(is, new TypeReference<>() {});
            for (JsonNode node : orders) {
                Order o = new Order();
                o.setId(node.get("id").asText());
                o.setCustomerId(node.get("customer_id").asText());
                o.setStatus(OrderStatus.fromValue(node.get("status").asText()));
                o.setTotal(node.get("total").asDouble());
                o.setCreatedAt(Instant.parse(node.get("created_at").asText()));
                o.setUpdatedAt(Instant.parse(node.get("updated_at").asText()));
                o.setTrackingNumber(node.has("tracking_number") && !node.get("tracking_number").isNull()
                        ? node.get("tracking_number").asText() : null);
                o.setShippingAddress(node.has("shipping_address") ? node.get("shipping_address").asText() : "");

                for (JsonNode itemNode : node.get("items")) {
                    OrderItem item = new OrderItem();
                    item.setProductId(itemNode.get("product_id").asInt());
                    item.setProductName(itemNode.get("product_name").asText());
                    item.setQuantity(itemNode.get("quantity").asInt());
                    item.setPrice(itemNode.get("price").asDouble());
                    o.addItem(item);
                }

                orderRepository.save(o);
            }
            log.info(String.format("Seeded %d orders", orders.size()));
        }
    }
}
