package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing a customer support ticket.
 *
 * <p>A support ticket captures a customer issue with a subject line,
 * detailed description, priority level, and processing status. Each
 * ticket is associated with a customer and timestamped at creation.</p>
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
