package dev.snbv2.cloudcart.support.model;

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
 * <p>Each order belongs to a customer and contains a list of {@link OrderItem} line items.
 * The order tracks its lifecycle through an {@link OrderStatus}, maintains a monetary total,
 * and records creation and update timestamps along with optional shipping details.</p>
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

    /**
     * Adds a line item to this order and establishes the bidirectional relationship
     * by setting the item's back-reference to this order.
     *
     * @param item the {@link OrderItem} to add
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
