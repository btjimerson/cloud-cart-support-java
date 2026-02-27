package dev.snbv2.cloudcart.customers.repository;

import dev.snbv2.cloudcart.customers.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {
}
