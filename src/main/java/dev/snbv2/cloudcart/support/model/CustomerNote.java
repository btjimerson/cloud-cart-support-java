package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing an interaction note recorded on a customer account.
 *
 * <p>Customer notes capture free-text observations or records of interactions
 * associated with a specific customer, each timestamped for chronological tracking.</p>
 */
@Entity
@Table(name = "customer_notes")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CustomerNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;
    private String note;
    private Instant timestamp;
}
