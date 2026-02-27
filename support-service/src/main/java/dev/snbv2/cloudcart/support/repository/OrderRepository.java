package dev.snbv2.cloudcart.support.repository;

import dev.snbv2.cloudcart.support.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Order} entities. Provides standard CRUD
 * operations with {@link String} as the primary key type, plus a custom query
 * method for finding orders by customer ID.
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * Finds all orders belonging to the specified customer.
     *
     * @param customerId the unique identifier of the customer
     * @return a list of orders associated with the given customer ID
     */
    List<Order> findByCustomerId(String customerId);
}
