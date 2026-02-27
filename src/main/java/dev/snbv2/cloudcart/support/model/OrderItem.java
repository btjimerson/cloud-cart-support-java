package dev.snbv2.cloudcart.support.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing a single line item within an {@link Order}.
 *
 * <p>Each order item references a product by ID and name, and records the
 * quantity ordered and the unit price at the time of purchase. The entity
 * maintains a many-to-one back-reference to its parent {@link Order}.</p>
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    private Integer productId;
    private String productName;
    private Integer quantity;
    private Double price;
}
