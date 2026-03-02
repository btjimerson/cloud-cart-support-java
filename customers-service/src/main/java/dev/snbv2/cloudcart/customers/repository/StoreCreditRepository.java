package dev.snbv2.cloudcart.customers.repository;

import dev.snbv2.cloudcart.customers.model.StoreCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link StoreCredit} entities.
 *
 * <p>Supports lookup of store credits by customer ID.</p>
 */
public interface StoreCreditRepository extends JpaRepository<StoreCredit, Long> {

    List<StoreCredit> findByCustomerId(String customerId);
}
