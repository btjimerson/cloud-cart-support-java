package dev.snbv2.cloudcart.orders.repository;

import dev.snbv2.cloudcart.orders.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Order} entities.
 *
 * <p>Supports lookup of orders by customer ID in addition to standard CRUD operations.</p>
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerId(String customerId);
}
