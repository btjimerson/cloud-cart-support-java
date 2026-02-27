package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Customer} entities. Provides standard CRUD
 * operations with {@link String} as the primary key type. No custom query methods are defined.
 */
public interface CustomerRepository extends JpaRepository<Customer, String> {
}
