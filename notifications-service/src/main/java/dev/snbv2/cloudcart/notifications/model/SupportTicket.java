package dev.snbv2.cloudcart.notifications.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing a customer support ticket.
 *
 * <p>Maps to the {@code support_tickets} table and includes fields for
 * subject, description, priority level, status, and creation timestamp.</p>
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;
    private String subject;

    @Column(length = 2000)
    private String description;

    private String priority;
    private String status;
    private Instant createdAt;
}
