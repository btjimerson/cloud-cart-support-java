package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing a store credit issued to a customer.
 *
 * <p>Store credits are monetary credits applied to a customer's account,
 * typically issued as part of returns, promotions, or customer service
 * resolutions. Each credit records the amount, the reason it was issued,
 * and the creation timestamp.</p>
 */
@Entity
@Table(name = "store_credits")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class StoreCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;
    private Double amount;
    private String reason;
    private Instant createdAt;
}
