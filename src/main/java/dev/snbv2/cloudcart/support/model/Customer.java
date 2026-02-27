package dev.snbv2.cloudcart.support.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing a customer in the system.
 *
 * <p>Each customer has a name, email address, loyalty points balance,
 * a comma-separated string of preferences, and a membership tier
 * (e.g., bronze, silver, gold).</p>
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Customer {

    @Id
    private String id;
    private String name;
    private String email;
    private Integer loyaltyPoints;
    private String preferences; // stored as comma-separated
    private String tier;
}
