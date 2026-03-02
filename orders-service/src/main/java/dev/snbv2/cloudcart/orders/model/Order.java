package dev.snbv2.cloudcart.orders.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a customer order.
 *
 * <p>Maps to the {@code orders} table and tracks order status, total amount,
 * shipping details, and associated {@link OrderItem}s.</p>
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Order {

    @Id
    private String id;
    private String customerId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Double total;
    private Instant createdAt;
    private Instant updatedAt;
    private String trackingNumber;
    private String shippingAddress;

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
