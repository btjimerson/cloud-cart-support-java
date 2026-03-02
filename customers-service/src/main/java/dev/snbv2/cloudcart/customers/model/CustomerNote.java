package dev.snbv2.cloudcart.customers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing an interaction note attached to a customer record.
 *
 * <p>Maps to the {@code customer_notes} table and stores timestamped notes
 * created during customer support interactions.</p>
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
