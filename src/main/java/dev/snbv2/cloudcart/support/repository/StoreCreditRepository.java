package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.StoreCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link StoreCredit} entities. Provides standard CRUD
 * operations with {@link Long} as the primary key type, plus a custom query method for
 * finding store credits by customer ID.
 */
public interface StoreCreditRepository extends JpaRepository<StoreCredit, Long> {

    /**
     * Finds all store credits issued to the specified customer.
     *
     * @param customerId the unique identifier of the customer
     * @return a list of store credits associated with the given customer ID
     */
    List<StoreCredit> findByCustomerId(String customerId);
}
