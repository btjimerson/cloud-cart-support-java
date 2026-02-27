package dev.snbv2.cloudcart.support.service.tools;

import dev.snbv2.cloudcart.support.model.Order;
import dev.snbv2.cloudcart.support.model.OrderStatus;
import dev.snbv2.cloudcart.support.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@link OrderToolsService}.
 * Verifies order operations against seeded data including retrieving order details,
 * listing customer orders, tracking shipments, cancelling orders, checking return
 * eligibility, initiating returns, and generating return labels. Tests cover both
 * success paths and error handling for non-existent orders and invalid state transitions.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.ai.anthropic.api-key=test-key"})
class OrderToolsServiceTest {

    @Autowired
    private OrderToolsService orderToolsService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Tests that retrieving an existing order returns its details including
     * order ID, status, total, and items.
     */
    @Test
    void getOrder_existingOrder_returnsDetails() {
        Map<String, Object> result = orderToolsService.getOrder("ORD-2024-0001");
        assertEquals("ORD-2024-0001", result.get("order_id"));
        assertNotNull(result.get("status"));
        assertNotNull(result.get("total"));
        assertNotNull(result.get("items"));
    }

    /**
     * Tests that retrieving a non-existent order returns an error map
     * containing the invalid order ID in the error message.
     */
    @Test
    void getOrder_nonExistent_returnsError() {
        Map<String, Object> result = orderToolsService.getOrder("ORD-FAKE");
        assertTrue(result.containsKey("error"));
        assertTrue(((String) result.get("error")).contains("ORD-FAKE"));
    }

    /**
     * Tests that listing orders for an existing customer returns a non-empty list
     * where all orders belong to the specified customer.
     */
    @Test
    @SuppressWarnings("unchecked")
    void listCustomerOrders_existingCustomer_returnsOrders() {
        Map<String, Object> result = orderToolsService.listCustomerOrders("CUST-001");
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.get("orders");
        int count = (int) result.get("count");
        assertTrue(count > 0);
        assertEquals(count, orders.size());
        assertTrue(orders.stream().allMatch(o -> "CUST-001".equals(o.get("customer_id"))));
    }

    /**
     * Tests that listing orders for a non-existent customer returns a count of zero.
     */
    @Test
    void listCustomerOrders_noOrders_returnsEmptyList() {
        Map<String, Object> result = orderToolsService.listCustomerOrders("CUST-NONEXISTENT");
        assertEquals(0, result.get("count"));
    }

    /**
     * Tests that tracking a delivered order returns delivery information
     * including a "delivered" status and delivery address.
     */
    @Test
    void trackShipment_deliveredOrder_returnsDeliveryInfo() {
        // Find a delivered order from seed data
        Order deliveredOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = orderToolsService.trackShipment(deliveredOrder.getId());
        assertEquals("delivered", result.get("status"));
        assertNotNull(result.get("delivered_to"));
    }

    /**
     * Tests that tracking a shipped order returns tracking details including
     * a "shipped" status and carrier information when a tracking number is present.
     */
    @Test
    void trackShipment_shippedOrder_returnsTrackingDetails() {
        Order shippedOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.SHIPPED)
                .findFirst()
                .orElse(null);

        if (shippedOrder != null) {
            Map<String, Object> result = orderToolsService.trackShipment(shippedOrder.getId());
            assertEquals("shipped", result.get("status"));
            if (shippedOrder.getTrackingNumber() != null) {
                assertEquals("UPS", result.get("carrier"));
            }
        }
    }

    /**
     * Tests that tracking a non-existent order returns an error.
     */
    @Test
    void trackShipment_nonExistentOrder_returnsError() {
        Map<String, Object> result = orderToolsService.trackShipment("ORD-FAKE");
        assertTrue(result.containsKey("error"));
    }

    /**
     * Tests that cancelling a pending order succeeds, returns a success message
     * containing "cancelled", and updates the order status in the database
     * to CANCELLED.
     */
    @Test
    void cancelOrder_pendingOrder_cancelsSuccessfully() {
        Order pendingOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .findFirst()
                .orElse(null);

        if (pendingOrder != null) {
            Map<String, Object> result = orderToolsService.cancelOrder(pendingOrder.getId());
            assertEquals(true, result.get("success"));
            assertTrue(((String) result.get("message")).contains("cancelled"));

            // Verify the order status changed in DB
            Order updated = orderRepository.findById(pendingOrder.getId()).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, updated.getStatus());
        }
    }

    /**
     * Tests that attempting to cancel an already-delivered order fails with a
     * "Cannot cancel" message.
     */
    @Test
    void cancelOrder_deliveredOrder_fails() {
        Order deliveredOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = orderToolsService.cancelOrder(deliveredOrder.getId());
        assertEquals(false, result.get("success"));
        assertTrue(((String) result.get("message")).contains("Cannot cancel"));
    }

    /**
     * Tests that attempting to cancel a non-existent order returns an error.
     */
    @Test
    void cancelOrder_nonExistentOrder_returnsError() {
        Map<String, Object> result = orderToolsService.cancelOrder("ORD-FAKE");
        assertTrue(result.containsKey("error"));
    }

    /**
     * Tests that checking return eligibility for a delivered order returns the
     * order ID, an eligibility flag, and a 30-day return window.
     */
    @Test
    void checkReturnEligibility_deliveredOrder_checksWindow() {
        Order deliveredOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = orderToolsService.checkReturnEligibility(deliveredOrder.getId());
        assertEquals(deliveredOrder.getId(), result.get("order_id"));
        assertNotNull(result.get("eligible"));
        assertEquals(30, result.get("return_window_days"));
    }

    /**
     * Tests that checking return eligibility for a pending or confirmed order
     * returns ineligible with a reason indicating that delivered status is required.
     */
    @Test
    void checkReturnEligibility_pendingOrder_notEligible() {
        Order pendingOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED)
                .findFirst()
                .orElse(null);

        if (pendingOrder != null) {
            Map<String, Object> result = orderToolsService.checkReturnEligibility(pendingOrder.getId());
            assertEquals(false, result.get("eligible"));
            assertTrue(((String) result.get("reason")).contains("delivered status"));
        }
    }

    /**
     * Tests that initiating a return on a delivered order succeeds, returns a
     * return ID and success message, and updates the order status to RETURN_REQUESTED.
     */
    @Test
    void initiateReturn_deliveredOrder_createsReturnRequest() {
        // Find a delivered order that hasn't been used by other tests
        Order deliveredOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = orderToolsService.initiateReturn(deliveredOrder.getId(), "Wrong size");
        assertEquals(true, result.get("success"));
        assertNotNull(result.get("return_id"));
        assertTrue(((String) result.get("message")).contains("initiated"));

        // Verify status changed
        Order updated = orderRepository.findById(deliveredOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.RETURN_REQUESTED, updated.getStatus());
    }

    /**
     * Tests that initiating a return on a non-delivered order (e.g., shipped)
     * fails and returns a failure response.
     */
    @Test
    void initiateReturn_nonDeliveredOrder_fails() {
        Order shippedOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.SHIPPED)
                .findFirst()
                .orElse(null);

        if (shippedOrder != null) {
            Map<String, Object> result = orderToolsService.initiateReturn(shippedOrder.getId(), "reason");
            assertEquals(false, result.get("success"));
        }
    }

    /**
     * Tests that generating a return label for an order with RETURN_REQUESTED status
     * succeeds and returns a label ID starting with "RL-" and a UPS carrier.
     */
    @Test
    void generateReturnLabel_returnRequestedOrder_generatesLabel() {
        // First initiate a return on a delivered order
        Order deliveredOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .findFirst()
                .orElse(null);

        if (deliveredOrder != null) {
            orderToolsService.initiateReturn(deliveredOrder.getId(), "Defective");
            Map<String, Object> result = orderToolsService.generateReturnLabel(deliveredOrder.getId());
            assertEquals(true, result.get("success"));
            assertNotNull(result.get("label_id"));
            assertTrue(((String) result.get("label_id")).startsWith("RL-"));
            assertEquals("UPS", result.get("carrier"));
        }
    }

    /**
     * Tests that generating a return label for an order not in RETURN_REQUESTED
     * status (e.g., shipped) fails and returns a failure response.
     */
    @Test
    void generateReturnLabel_nonReturnRequestedOrder_fails() {
        Order shippedOrder = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.SHIPPED)
                .findFirst()
                .orElse(null);

        if (shippedOrder != null) {
            Map<String, Object> result = orderToolsService.generateReturnLabel(shippedOrder.getId());
            assertEquals(false, result.get("success"));
        }
    }
}
