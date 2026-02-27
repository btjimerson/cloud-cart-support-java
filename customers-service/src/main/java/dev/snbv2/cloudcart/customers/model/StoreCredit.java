package dev.snbv2.cloudcart.customers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

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
