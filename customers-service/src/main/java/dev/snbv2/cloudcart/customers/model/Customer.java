package dev.snbv2.cloudcart.customers.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing a customer account.
 *
 * <p>Maps to the {@code customers} table and includes fields for contact info,
 * loyalty points, tier level, and preferences.</p>
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
    private String preferences;
    private String tier;
}
