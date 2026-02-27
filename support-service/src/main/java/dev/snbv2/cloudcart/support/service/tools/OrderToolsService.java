package dev.snbv2.cloudcart.support.service.tools;

import dev.snbv2.cloudcart.support.model.*;
import dev.snbv2.cloudcart.support.repository.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service providing order-related tool implementations for agent interactions.
 * Supports retrieving order details, listing customer orders, tracking shipments,
 * cancelling orders, checking return eligibility, initiating returns, and generating
 * return shipping labels. All methods return results as {@link Map} structures suitable
 * for serialization in agent tool call responses.
 */
@Service
public class OrderToolsService {

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;

    /**
     * Constructs a new {@code OrderToolsService} with the required repositories.
     *
     * @param orderRepository         the repository for accessing order data
     * @param returnRequestRepository the repository for persisting return requests
     */
    public OrderToolsService(OrderRepository orderRepository,
                             ReturnRequestRepository returnRequestRepository) {
        this.orderRepository = orderRepository;
        this.returnRequestRepository = returnRequestRepository;
    }

    /**
     * Retrieves the details of a single order by its ID.
     *
     * @param orderId the unique identifier of the order
     * @return a map containing the order details, or an error entry if the order is not found
     */
    public Map<String, Object> getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .map(this::orderToMap)
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    /**
     * Lists all orders for a given customer.
     *
     * @param customerId the unique identifier of the customer
     * @return a map containing a list of order maps under the "orders" key and the total count
     */
    public Map<String, Object> listCustomerOrders(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        List<Map<String, Object>> orderMaps = orders.stream().map(this::orderToMap).toList();
        return Map.of("orders", orderMaps, "count", orderMaps.size());
    }

    /**
     * Retrieves shipment tracking information for the specified order. Returns mock
     * carrier and tracking details for shipped orders, delivery confirmation for
     * delivered orders, or an informational message for other statuses.
     *
     * @param orderId the unique identifier of the order to track
     * @return a map containing tracking information, or an error entry if the order is not found
     */
    public Map<String, Object> trackShipment(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("order_id", order.getId());
                    result.put("status", order.getStatus().getValue());
                    result.put("tracking_number", order.getTrackingNumber());

                    if (order.getTrackingNumber() != null && order.getStatus() == OrderStatus.SHIPPED) {
                        result.put("carrier", "UPS");
                        result.put("estimated_delivery", Instant.now().plus(3, ChronoUnit.DAYS).toString());
                        result.put("last_location", "Distribution Center, Memphis, TN");
                        result.put("last_update", Instant.now().minus(6, ChronoUnit.HOURS).toString());
                    } else if (order.getStatus() == OrderStatus.DELIVERED) {
                        result.put("carrier", "UPS");
                        result.put("delivered_at", order.getUpdatedAt().toString());
                        result.put("delivered_to", order.getShippingAddress());
                    } else {
                        result.put("message", "No tracking information available for this order status");
                    }
                    return result;
                })
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    /**
     * Cancels the specified order if it is in a cancellable state (PENDING or CONFIRMED).
     * Updates the order status to CANCELLED and returns the refund amount. Orders in
     * other statuses cannot be cancelled.
     *
     * @param orderId the unique identifier of the order to cancel
     * @return a map indicating success or failure with a descriptive message, or an error
     *         entry if the order is not found
     */
    public Map<String, Object> cancelOrder(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED) {
                        order.setStatus(OrderStatus.CANCELLED);
                        order.setUpdatedAt(Instant.now());
                        orderRepository.save(order);
                        return Map.<String, Object>of(
                                "success", true,
                                "order_id", orderId,
                                "message", "Order has been cancelled successfully",
                                "refund_amount", order.getTotal()
                        );
                    } else {
                        return Map.<String, Object>of(
                                "success", false,
                                "order_id", orderId,
                                "message", "Cannot cancel order with status: " + order.getStatus().getValue()
                        );
                    }
                })
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    /**
     * Checks whether the specified order is eligible for a return. An order must be
     * in DELIVERED status and within the 30-day return window to be eligible.
     *
     * @param orderId the unique identifier of the order to check
     * @return a map containing eligibility status, days since delivery, return window,
     *         and a reason if ineligible, or an error entry if the order is not found
     */
    public Map<String, Object> checkReturnEligibility(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("order_id", orderId);
                    result.put("status", order.getStatus().getValue());

                    boolean eligible = order.getStatus() == OrderStatus.DELIVERED;
                    if (eligible) {
                        long daysSinceDelivery = ChronoUnit.DAYS.between(order.getUpdatedAt(), Instant.now());
                        eligible = daysSinceDelivery <= 30;
                        result.put("days_since_delivery", daysSinceDelivery);
                        result.put("return_window_days", 30);
                    }

                    result.put("eligible", eligible);
                    if (!eligible) {
                        if (order.getStatus() != OrderStatus.DELIVERED) {
                            result.put("reason", "Order must be in delivered status to initiate a return");
                        } else {
                            result.put("reason", "Return window has expired (30 days from delivery)");
                        }
                    }
                    return result;
                })
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    /**
     * Initiates a return for the specified order. Only orders in DELIVERED status can
     * be returned. Updates the order status to RETURN_REQUESTED and creates a new
     * {@link ReturnRequest} record.
     *
     * @param orderId the unique identifier of the order to return
     * @param reason  the customer's reason for returning the order
     * @return a map indicating success with the return ID and next steps, or failure
     *         with a descriptive message, or an error entry if the order is not found
     */
    public Map<String, Object> initiateReturn(String orderId, String reason) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        return Map.<String, Object>of(
                                "success", false,
                                "message", "Only delivered orders can be returned"
                        );
                    }

                    order.setStatus(OrderStatus.RETURN_REQUESTED);
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);

                    ReturnRequest rr = new ReturnRequest();
                    rr.setOrderId(orderId);
                    rr.setReason(reason);
                    rr.setStatus("pending");
                    rr.setCreatedAt(Instant.now());
                    returnRequestRepository.save(rr);

                    return Map.<String, Object>of(
                            "success", true,
                            "order_id", orderId,
                            "return_id", rr.getId(),
                            "message", "Return request initiated successfully",
                            "next_steps", "A return shipping label will be generated for you"
                    );
                })
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    /**
     * Generates a return shipping label for the specified order. The order must be in
     * RETURN_REQUESTED status. Returns a mock label ID, carrier information, return
     * address, and shipping fee.
     *
     * @param orderId the unique identifier of the order for which to generate a return label
     * @return a map containing the label details on success, or failure with a descriptive
     *         message, or an error entry if the order is not found
     */
    public Map<String, Object> generateReturnLabel(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getStatus() != OrderStatus.RETURN_REQUESTED) {
                        return Map.<String, Object>of(
                                "success", false,
                                "message", "Return must be requested before generating a label"
                        );
                    }

                    String labelId = "RL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    return Map.<String, Object>of(
                            "success", true,
                            "order_id", orderId,
                            "label_id", labelId,
                            "carrier", "UPS",
                            "return_address", "Agentic Cart Returns, 1500 Commerce Drive, Dallas, TX 75201",
                            "shipping_fee", 5.99,
                            "message", "Return label generated. Please print and attach to your package."
                    );
                })
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    /**
     * Converts an {@link Order} entity and its associated items into a map representation
     * suitable for serialization in agent tool call responses.
     *
     * @param order the order entity to convert
     * @return a map containing the order's fields and a nested list of item maps
     */
    private Map<String, Object> orderToMap(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("order_id", order.getId());
        map.put("customer_id", order.getCustomerId());
        map.put("status", order.getStatus().getValue());
        map.put("total", order.getTotal());
        map.put("created_at", order.getCreatedAt().toString());
        map.put("updated_at", order.getUpdatedAt().toString());
        map.put("tracking_number", order.getTrackingNumber());
        map.put("shipping_address", order.getShippingAddress());

        List<Map<String, Object>> items = order.getItems().stream().map(item -> {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("product_id", item.getProductId());
            itemMap.put("product_name", item.getProductName());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("price", item.getPrice());
            return itemMap;
        }).toList();
        map.put("items", items);

        return map;
    }
}
