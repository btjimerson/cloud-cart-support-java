package dev.snbv2.cloudcart.orders.tools;

import dev.snbv2.cloudcart.orders.model.*;
import dev.snbv2.cloudcart.orders.repository.OrderRepository;
import dev.snbv2.cloudcart.orders.repository.ReturnRequestRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * MCP tool methods for order management operations.
 *
 * <p>Provides AI-accessible tools for checking order status, tracking shipments,
 * cancelling orders, evaluating return eligibility, initiating returns,
 * and generating return shipping labels.</p>
 */
@Service
public class OrderTools {

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;

    public OrderTools(OrderRepository orderRepository, ReturnRequestRepository returnRequestRepository) {
        this.orderRepository = orderRepository;
        this.returnRequestRepository = returnRequestRepository;
    }

    @Tool(description = "Retrieve the current status and details of an order by order ID")
    public Map<String, Object> getOrderStatus(
            @ToolParam(description = "The unique order identifier") String orderId) {
        return orderRepository.findById(orderId)
                .map(this::orderToMap)
                .orElse(Map.of("error", "Order not found: " + orderId));
    }

    @Tool(description = "List all orders for a given customer")
    public Map<String, Object> listCustomerOrders(
            @ToolParam(description = "The customer ID") String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        List<Map<String, Object>> orderMaps = orders.stream().map(this::orderToMap).toList();
        return Map.of("orders", orderMaps, "count", orderMaps.size());
    }

    @Tool(description = "Get shipping tracking information for an order")
    public Map<String, Object> getTrackingInfo(
            @ToolParam(description = "The unique order identifier") String orderId) {
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

    @Tool(description = "Cancel an order if it hasn't shipped yet")
    public Map<String, Object> cancelOrder(
            @ToolParam(description = "The unique order identifier") String orderId) {
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

    @Tool(description = "Check if an order is eligible for return")
    public Map<String, Object> checkReturnEligibility(
            @ToolParam(description = "The unique order identifier") String orderId) {
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

    @Tool(description = "Initiate a return for an order")
    public Map<String, Object> initiateReturn(
            @ToolParam(description = "The unique order identifier") String orderId,
            @ToolParam(description = "Reason for the return") String reason) {
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

    @Tool(description = "Generate a return shipping label for an order")
    public Map<String, Object> generateReturnLabel(
            @ToolParam(description = "The unique order identifier") String orderId) {
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
