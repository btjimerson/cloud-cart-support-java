package dev.snbv2.cloudcart.orders.repository;

import dev.snbv2.cloudcart.orders.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link OrderItem} entities.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
