package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing a supervisor escalation for a customer issue.
 *
 * <p>Escalations are created when a customer interaction requires supervisor
 * review or intervention. Each escalation records the associated customer,
 * the reason for escalation, the current processing status, and the
 * creation timestamp.</p>
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
