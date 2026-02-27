package dev.snbv2.cloudcart.support.config;

import dev.snbv2.cloudcart.support.model.*;
import dev.snbv2.cloudcart.support.repository.*;
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
 * A {@link CommandLineRunner} that seeds the H2 database with initial data on application startup.
 *
 * <p>Reads product, customer, and order data from JSON files located in the
 * {@code seed-data/} classpath directory and persists them to the database via
 * the corresponding Spring Data repositories.
 */
@Component
@CommonsLog
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new {@code DataSeeder} with the required repositories and object mapper.
     *
     * @param productRepository      the repository for persisting product entities
     * @param orderRepository        the repository for persisting order entities
     * @param customerRepository     the repository for persisting customer entities
     * @param customerNoteRepository the repository for persisting customer note entities
     * @param objectMapper           the Jackson object mapper for deserializing JSON seed data
     */
    public DataSeeder(ProductRepository productRepository,
                      OrderRepository orderRepository,
                      CustomerRepository customerRepository,
                      CustomerNoteRepository customerNoteRepository,
                      ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the data seeding process when the application starts.
     *
     * <p>Seeds products, customers, and orders in sequence, then logs a success message.
     *
     * @param args command-line arguments (not used)
     * @throws Exception if any error occurs during data seeding
     */
    @Override
    public void run(String... args) throws Exception {
        seedProducts();
        seedCustomers();
        seedOrders();
        log.info("Seed data loaded successfully");
    }

    /**
     * Reads product data from {@code seed-data/products.json} and persists each product
     * to the database.
     *
     * <p>Each product JSON object is mapped to a {@link Product} entity with fields
     * including id, name, description, price, category, image URL, stock status,
     * and stock quantity.
     *
     * @throws Exception if the JSON file cannot be read or parsed
     */
    private void seedProducts() throws Exception {
        try (InputStream is = new ClassPathResource("seed-data/products.json").getInputStream()) {
            List<JsonNode> products = objectMapper.readValue(is, new TypeReference<>() {});
            for (JsonNode node : products) {
                Product p = new Product();
                p.setId(node.get("id").asInt());
                p.setName(node.get("name").asText());
                p.setDescription(node.get("description").asText());
                p.setPrice(node.get("price").asDouble());
                p.setCategory(node.get("category").asText());
                p.setImageUrl(node.has("image_url") ? node.get("image_url").asText() : "");
                p.setInStock(node.get("in_stock").asBoolean());
                p.setStockQuantity(node.get("stock_quantity").asInt());
                productRepository.save(p);
            }
            log.info(String.format("Seeded %d products", products.size()));
        }
    }

    /**
     * Reads customer data from {@code seed-data/customers.json} and persists each customer
     * and their associated notes to the database.
     *
     * <p>Customer preferences are stored as a comma-separated string. Loyalty tiers are
     * determined based on loyalty points: Platinum (5000+), Gold (3000+), Silver (1000+),
     * or Bronze (below 1000). Any notes associated with the customer are also persisted
     * as {@link CustomerNote} entities.
     *
     * @throws Exception if the JSON file cannot be read or parsed
     */
    private void seedCustomers() throws Exception {
        try (InputStream is = new ClassPathResource("seed-data/customers.json").getInputStream()) {
            List<JsonNode> customers = objectMapper.readValue(is, new TypeReference<>() {});
            for (JsonNode node : customers) {
                Customer c = new Customer();
                c.setId(node.get("id").asText());
                c.setName(node.get("name").asText());
                c.setEmail(node.get("email").asText());
                c.setLoyaltyPoints(node.get("loyalty_points").asInt());

                // Store preferences as comma-separated string
                List<String> prefs = objectMapper.convertValue(node.get("preferences"), new TypeReference<>() {});
                c.setPreferences(String.join(",", prefs));

                // Determine tier based on loyalty points
                int points = c.getLoyaltyPoints();
                if (points >= 5000) c.setTier("Platinum");
                else if (points >= 3000) c.setTier("Gold");
                else if (points >= 1000) c.setTier("Silver");
                else c.setTier("Bronze");

                customerRepository.save(c);

                // Seed customer notes
                List<String> notes = objectMapper.convertValue(node.get("notes"), new TypeReference<>() {});
                for (String noteText : notes) {
                    CustomerNote note = new CustomerNote();
                    note.setCustomerId(c.getId());
                    note.setNote(noteText);
                    note.setTimestamp(Instant.now());
                    customerNoteRepository.save(note);
                }
            }
            log.info(String.format("Seeded %d customers", customers.size()));
        }
    }

    /**
     * Reads order data from {@code seed-data/orders.json} and persists each order
     * along with its line items to the database.
     *
     * <p>Each order JSON object is mapped to an {@link Order} entity with associated
     * {@link OrderItem} entries. Order status values are converted via
     * {@link OrderStatus#fromValue(String)}.
     *
     * @throws Exception if the JSON file cannot be read or parsed
     */
    private void seedOrders() throws Exception {
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

                // Parse order items
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
