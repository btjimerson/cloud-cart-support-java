package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity representing a product return request submitted by a customer.
 *
 * <p>A return request is associated with a specific order and includes the
 * reason for the return, the current processing status, and the timestamp
 * when the request was created.</p>
 */
@Entity
@Table(name = "return_requests")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderId;
    private String reason;
    private String status;
    private Instant createdAt;
}
