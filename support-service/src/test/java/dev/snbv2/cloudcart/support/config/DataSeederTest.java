package dev.snbv2.cloudcart.support.config;

import dev.snbv2.cloudcart.support.model.Customer;
import dev.snbv2.cloudcart.support.model.Order;
import dev.snbv2.cloudcart.support.model.Product;
import dev.snbv2.cloudcart.support.repository.CustomerNoteRepository;
import dev.snbv2.cloudcart.support.repository.CustomerRepository;
import dev.snbv2.cloudcart.support.repository.OrderRepository;
import dev.snbv2.cloudcart.support.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link DataSeeder} component.
 * Verifies that seed data is correctly loaded into the database on application startup,
 * including correct record counts, required field presence, tier assignments, and
 * referential integrity between orders and customers.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "kagent.a2a.base-url=http://localhost:8083",
        "kagent.a2a.namespace=kagent"
})
class DataSeederTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerNoteRepository customerNoteRepository;

    /**
     * Tests that exactly 50 products are seeded into the database.
     */
    @Test
    void productsSeeded_correctCount() {
        assertEquals(50, productRepository.count());
    }

    /**
     * Tests that all seeded products have non-null, non-blank required fields
     * including id, name, description, category, and a positive price.
     */
    @Test
    void productsSeeded_haveRequiredFields() {
        List<Product> products = productRepository.findAll();
        for (Product p : products) {
            assertNotNull(p.getId());
            assertNotNull(p.getName());
            assertFalse(p.getName().isBlank());
            assertNotNull(p.getDescription());
            assertNotNull(p.getCategory());
            assertTrue(p.getPrice() > 0);
        }
    }

    /**
     * Tests that the seeded products include all expected categories.
     */
    @Test
    void productsSeeded_categoriesPresent() {
        List<String> categories = productRepository.findAll().stream()
                .map(Product::getCategory)
                .distinct()
                .toList();
        assertTrue(categories.contains("electronics"));
        assertTrue(categories.contains("kitchen"));
        assertTrue(categories.contains("sports"));
        assertTrue(categories.contains("beauty"));
        assertTrue(categories.contains("toys"));
        assertTrue(categories.contains("home"));
        assertTrue(categories.contains("pets"));
        assertTrue(categories.contains("office"));
        assertTrue(categories.contains("automotive"));
        assertTrue(categories.contains("tools"));
    }

    /**
     * Tests that exactly 10 customers are seeded into the database.
     */
    @Test
    void customersSeeded_correctCount() {
        assertEquals(10, customerRepository.count());
    }

    /**
     * Tests that all seeded customers have a valid loyalty tier assigned,
     * which must be one of Bronze, Silver, Gold, or Platinum.
     */
    @Test
    void customersSeeded_tiersAssigned() {
        List<Customer> customers = customerRepository.findAll();
        for (Customer c : customers) {
            assertNotNull(c.getTier());
            assertTrue(List.of("Bronze", "Silver", "Gold", "Platinum").contains(c.getTier()));
        }
    }

    /**
     * Tests that at least some customer notes are loaded from the seed data.
     */
    @Test
    void customersSeeded_notesLoaded() {
        // At least some customers should have notes from seed data
        assertTrue(customerNoteRepository.count() > 0);
    }

    /**
     * Tests that exactly 20 orders are seeded into the database.
     */
    @Test
    void ordersSeeded_correctCount() {
        assertEquals(20, orderRepository.count());
    }

    /**
     * Tests that all seeded orders have a non-null status, creation timestamp,
     * total, and at least one line item.
     */
    @Test
    void ordersSeeded_haveItemsAndStatus() {
        List<Order> orders = orderRepository.findAll();
        for (Order o : orders) {
            assertNotNull(o.getStatus());
            assertNotNull(o.getCreatedAt());
            assertNotNull(o.getTotal());
            assertFalse(o.getItems().isEmpty(), "Order " + o.getId() + " has no items");
        }
    }

    /**
     * Tests that the specific order ORD-2024-0001 exists with the expected
     * customer ID and a positive total.
     */
    @Test
    void ordersSeeded_specificOrderExists() {
        Optional<Order> order = orderRepository.findById("ORD-2024-0001");
        assertTrue(order.isPresent());
        assertEquals("CUST-001", order.get().getCustomerId());
        assertTrue(order.get().getTotal() > 0);
    }

    /**
     * Tests referential integrity: every order's customer ID must reference
     * an existing customer in the database.
     */
    @Test
    void ordersSeeded_customerIdsReferenceExistingCustomers() {
        List<Order> orders = orderRepository.findAll();
        for (Order o : orders) {
            assertTrue(customerRepository.existsById(o.getCustomerId()),
                    "Order " + o.getId() + " references non-existent customer " + o.getCustomerId());
        }
    }
}
