package dev.snbv2.cloudcart.notifications.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing an issue escalation to a supervisor.
 *
 * <p>Maps to the {@code escalations} table and tracks the customer,
 * reason for escalation, status, and creation timestamp.</p>
 */
@Entity
@Table(name = "escalations")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;

    @Column(length = 2000)
    private String reason;

    private String status;
    private Instant createdAt;
}
